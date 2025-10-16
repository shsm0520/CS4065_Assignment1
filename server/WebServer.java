 //Seunghun Lee
 
import java.io.*;
import java.net.*;
import java.util.*;

public final class WebServer {
    public static void main(String argv[]) throws Exception {
        // Set the port number.
        int port = 6789;
        
        System.out.println("Web Server starting on port " + port + "...");
        
        // Establish the listen socket.
        ServerSocket listenSocket = new ServerSocket(port);
        
        System.out.println("Web Server started successfully!");
        System.out.println("Waiting for connections...\n");
        
        // Process HTTP service requests in an infinite loop.
        while (true) {
            // Listen for a TCP connection request.
            Socket connectionSocket = listenSocket.accept();
            
            // Construct an object to process the HTTP request message.
            HttpRequest req = new HttpRequest(connectionSocket);
            
            // Create a new thread to process the request.
            Thread thread = new Thread(req);
            
            // Start the thread.
            thread.start();
        }
    }
}

final class HttpRequest implements Runnable {
    final static String CRLF = "\r\n";
    Socket socket;
    
    // Server Configuration root dir setting implimented : basics of program
    // Basically will be ../public/ folder alongh with server
    // private static final String DOCUMENT_ROOT = "../Public/";
    // To meet assignment requriements it set as root 
    private static final String DOCUMENT_ROOT = "../Public/";  
    
    // FTP Configuration 
    private static final String FTP_SERVER = "127.0.0.1";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "testuser";  // Docker set id 
    private static final String FTP_PASS = "testpass";  // Docker set pw
    
    // Constructor
    public HttpRequest(Socket socket) throws Exception {
        this.socket = socket;
    }
    
    // Implement the run() method from Runnable interface.
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            System.out.println("Error request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void processRequest() throws Exception {
        // Get a reference to the socket's input and output streams.
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
        
        // Set up input stream filters.
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        
        // Get the request line of the HTTP request message.
        String requestLine = br.readLine();
        
        // Display the request line.
        System.out.println();
        System.out.println("==================");
        System.out.println("Request:");
        System.out.println(requestLine);
        
        // Get and display the header lines.
        String headerLine = null;
        while ((headerLine = br.readLine()).length() != 0) {
            System.out.println(headerLine);
        }
        
        // Extract the filename from the request line by tokenizing string.
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken(); // skip over the method, which should be "GET"
        String fileName = tokens.nextToken();
        
        // Handle root path check following files index.html, index.htm, default.html 
        // can add more by adding more in candidated and first appear will be used
        if (fileName.equals("/")) {
            String[] candidates = {"/index.html", "/index.htm", "/default.html"};

            fileName = Arrays.stream(candidates)
            .filter(f -> new File(DOCUMENT_ROOT+ f).exists())
            .findFirst()
            .orElse("/index.html");
        }
        
        // Prepend the document root
        fileName = DOCUMENT_ROOT + fileName.substring(1);  // Remove leading "/"
        
        System.out.println("Requested file path: " + fileName);
        
        // Open the requested file.
        FileInputStream fis = null;
        boolean fileExists = true;
        try {
            fis = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            fileExists = false;
        }
        
        // Construct the response message.
        String statusLine = null;
        String contentTypeLine = null;
        String entityBody = null;
        
        if (fileExists) {
            statusLine = "HTTP/1.0 200 OK" + CRLF;
            contentTypeLine = "Content-type: " + contentType(fileName) + CRLF;
        } else {
            // if the file requested is any type other than a text (.txt) file, report 
            // error to the web client
            if (!contentType(fileName).equalsIgnoreCase("text/plain")) {
                statusLine = "HTTP/1.0 404 Not Found" + CRLF;
                contentTypeLine = "Content-type: text/html" + CRLF;
                entityBody = "<HTML>" + 
                    "<HEAD><TITLE>Not Found</TITLE></HEAD>" +
                    "<BODY><H1>404 Not Found</H1>" +
                    "<P>The requested was not found.</P></BODY></HTML>";
            } else { 
                // else retrieve the text (.txt) file from your local FTP server
                System.out.println("File not found locally. Attempting FTP download...");
                
                try {
                    // create an instance of ftp client
                    FtpClient ftpClient = new FtpClient();
                    
                    // connect to the ftp server
                    boolean connected = ftpClient.connect(FTP_SERVER, FTP_PORT, FTP_USER, FTP_PASS);
                    
                    if (connected) {
                        // retrieve the file from the ftp server
                        // Extract just the filename (remove path)
                        String ftpFileName = new File(fileName).getName();
                        boolean downloaded = ftpClient.download(ftpFileName, fileName);
                        
                        // disconnect from ftp server
                        ftpClient.disconnect();
                        
                        if (downloaded) {
                            System.out.println("File successfully downloaded from FTP server.");
                            // assign input stream to read the recently ftp-downloaded file
                            fis = new FileInputStream(fileName);
                            fileExists = true;
                            statusLine = "HTTP/1.0 200 OK" + CRLF;
                            contentTypeLine = "Content-type: " + contentType(fileName) + CRLF;
                        } else {
                            System.out.println("Failed to download file from FTP server.");
                            statusLine = "HTTP/1.0 404 Not Found" + CRLF;
                            contentTypeLine = "Content-type: text/html" + CRLF;
                            entityBody = "<HTML>" + 
                                "<HEAD><TITLE>Not Found</TITLE></HEAD>" +
                                "<BODY><H1>404 Not Found</H1>" +
                                "<P>The requested file not found on this server or FTP server.</P></BODY></HTML>";
                        }
                    } else {
                        System.out.println("Failed to connect to FTP server.");
                        statusLine = "HTTP/1.0 503 Service Unavailable" + CRLF;
                        contentTypeLine = "Content-type: text/html" + CRLF;
                        entityBody = "<HTML>" + 
                            "<HEAD><TITLE>Service Unavailable</TITLE></HEAD>" +
                            "<BODY><H1>503 Service Unavailable</H1>" +
                            "<P>Could not connect to FTP server.</P></BODY></HTML>";
                    }
                } catch (Exception e) {
                    System.out.println("FTP error: " + e.getMessage());
                    statusLine = "HTTP/1.0 500 Internal Server Error" + CRLF;
                    contentTypeLine = "Content-type: text/html" + CRLF;
                    entityBody = "<HTML>" + 
                        "<HEAD><TITLE>Internal Server Error</TITLE></HEAD>" +
                        "<BODY><H1>500 Internal Server Error</H1>" +
                        "<P>Internal error occured.</P></BODY></HTML>";
                }
            }
        }
        
        // Display the response
        System.out.println();
        System.out.println("Response:");
        System.out.println(statusLine.trim());
        System.out.println(contentTypeLine.trim());
        if (entityBody != null && !fileExists) {
            System.out.println();
            System.out.println("Entity Body: " + entityBody);
        }
        System.out.println("==================\n");
        
        // Send the status line.
        os.writeBytes(statusLine);
        
        // Send the content type line.
        os.writeBytes(contentTypeLine);
        
        // Send a blank line to indicate the end of the header lines.
        os.writeBytes(CRLF);
        
        // Send the entity body.
        if (fileExists) {
            sendBytes(fis, os);
            fis.close();
        } else {
            if (entityBody != null) {
                os.writeBytes(entityBody);
            }
        }
        
        // Close streams and socket.
        os.close();
        br.close();
        socket.close();
    }
    
    private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception {
        // Construct a 1K buffer to hold bytes on their way to the socket.
        byte[] buffer = new byte[1024];
        int bytes = 0;
        
        // Copy requested file into the socket's output stream.
        while ((bytes = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytes);
        }
    }
    
    private static String contentType(String fileName) {
        if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".txt")) {
            return "text/plain";
        }
        if (fileName.endsWith(".css")) {
            return "text/css";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript";
        }
        return "application/octet-stream";
    }
}