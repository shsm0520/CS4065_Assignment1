 //Seunghun Lee
 
import java.io.*;
import java.net.*;

public class FtpClient {
    private static final boolean DEBUG = true;
    private Socket controlSocket;
    private BufferedReader controlInput;
    private PrintWriter controlOutput;
    
    public FtpClient() {
        // Constructor
        this.controlSocket = null;
        this.controlInput = null;
        this.controlOutput = null;
    }
    
    /**
     * Connect to FTP server and login
     */
    public boolean connect(String server, int port, String username, String password) {
        try {
            if (DEBUG) System.out.println("FTP -  Connecting to " + server + ":" + port);
            
            // Establish control connection
            controlSocket = new Socket(server, port);
            controlInput = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            controlOutput = new PrintWriter(controlSocket.getOutputStream(), true);
            
            // Read welcome message (220)
            String response = readResponse();
            if (DEBUG) System.out.println("FTP -  " + response);
            
            if (!response.startsWith("220")) {
                return false;
            }
            
            // Send username
            sendCommand("USER " + username);
            response = readResponse();
            if (DEBUG) System.out.println("FTP -  " + response);
            
            // Send password
            sendCommand("PASS " + password);
            response = readResponse();
            if (DEBUG) System.out.println("FTP -  " + response);
            
            // Check if login successful (230)
            if (response.startsWith("230")) {
                if (DEBUG) System.out.println("FTP -  Login success");
                return true;
            } else {
                if (DEBUG) System.out.println("FTP -  Login fail");
                return false;
            }
            
        } catch (IOException e) {
            if (DEBUG) System.out.println("FTP -  Connection fail: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Download a file from FTP server using passive mode
     */
    public boolean download(String remoteFile, String localFile) {
        try {
            if (DEBUG) System.out.println("FTP -  Downloading: " + remoteFile);
            
            // Set binary mode for file transfer
            sendCommand("TYPE I");
            String typeResponse = readResponse();
            if (DEBUG) System.out.println("FTP -  " + typeResponse);
            
            // Enter passive mode
            sendCommand("PASV");
            String pasvResponse = readResponse();
            if (DEBUG) System.out.println("FTP -  " + pasvResponse);
            
            if (!pasvResponse.startsWith("227")) {
                if (DEBUG) System.out.println("FTP -  Failed to enter passive mode");
                return false;
            }
            
            // Parse PASV response to get data connection info
            // Format: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
            int start = pasvResponse.indexOf('(');
            int end = pasvResponse.indexOf(')');
            
            if (start == -1 || end == -1) {
                if (DEBUG) System.out.println("FTP -  Failed to parse PASV response");
                return false;
            }
            
            String[] parts = pasvResponse.substring(start + 1, end).split(",");
            
            if (parts.length != 6) {
                if (DEBUG) System.out.println("FTP -  Invalid PASV response format");
                return false;
            }
            
            String dataHost = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            int dataPort = Integer.parseInt(parts[4].trim()) * 256 + Integer.parseInt(parts[5].trim());
            
            if (DEBUG) System.out.println("FTP -  Data connection: " + dataHost + ":" + dataPort);
            
            // Establish data connection
            Socket dataSocket = new Socket(dataHost, dataPort);
            InputStream dataInput = dataSocket.getInputStream();
            
            // Request file transfer
            sendCommand("RETR " + remoteFile);
            String retrResponse = readResponse();
            if (DEBUG) System.out.println("FTP -  " + retrResponse);
            
            if (!retrResponse.startsWith("150") && !retrResponse.startsWith("125")) {
                if (DEBUG) System.out.println("FTP -  File transfer failed to start");
                dataSocket.close();
                return false;
            }
            
            // Download file
            FileOutputStream fileOutput = new FileOutputStream(localFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalBytes = 0;
            
            while ((bytesRead = dataInput.read(buffer)) != -1) {
                fileOutput.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            fileOutput.close();
            dataSocket.close();
            
            if (DEBUG) System.out.println("FTP -  Downloaded " + totalBytes + " bytes");
            
            // Read transfer completion response (226)
            String completeResponse = readResponse();
            if (DEBUG) System.out.println("FTP -  " + completeResponse);
            
            return completeResponse.startsWith("226");
            
        } catch (IOException e) {
            if (DEBUG) System.out.println("FTP -  Download failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Disconnect from FTP server
     */
    public void disconnect() {
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                sendCommand("QUIT");
                String response = readResponse();
                if (DEBUG) System.out.println("FTP -  " + response);
                
                controlInput.close();
                controlOutput.close();
                controlSocket.close();
                
                if (DEBUG) System.out.println("FTP -  Disconnected successfully");
            }
        } catch (IOException e) {
            if (DEBUG) System.out.println("FTP -  Disconnect error: " + e.getMessage());
        }
    }
    
    /**
     * Send a command to FTP server
     */
    private void sendCommand(String command) {
        if (DEBUG) System.out.println("FTP -  >>> " + command);
        controlOutput.println(command);
        controlOutput.flush();
    }
    
    /**
     * Read response from FTP server
     */
    private String readResponse() throws IOException {
        String response = controlInput.readLine();
        
        // Handle multi-line responses
        if (response != null && response.length() >= 4 && response.charAt(3) == '-') {
            String code = response.substring(0, 3);
            String line;
            while ((line = controlInput.readLine()) != null) {
                if (DEBUG) System.out.println("FTP -  <<< " + line);
                if (line.startsWith(code + " ")) {
                    response = line;
                    break;
                }
            }
        }
        
        return response;
    }
}