import java.lang.System;
import java.io.IOException;

import java.io.*;
import java.net.*;

import java.util.Arrays;

//
// This is an implementation of a simplified version of a command
// line ftp client. The program always takes two arguments
//


/**
 * A FTP client
 */
public class CSftp {
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;

    static final String REQUEST_PREFIX = "--> ";
    static final String RESPONSE_PREFIX = "<-- ";

    // array of supported (valid) commands
    static final String[] validCommands = {
        "quit",
        "user",
        "pw",
        "cd",
        "dir",
        "get",
        "features"
    };

    public static void main(String [] args) {
        byte cmdString[] = new byte[MAX_LEN];
        String command;

        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
            // then exit.
        if (args.length != 2 && args.length != 1) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        String hostName = args[0];
        int hostPort = 21;
        if (args.length == 2) {
            hostPort = Integer.parseInt(args[1]);
        }

        try (
            // create a socket
            Socket socket = new Socket(hostName, hostPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            // set socket timeout for reading responses
            socket.setSoTimeout(500);

            printResponse(in);

            for (int len = 1; len > 0;) {
                System.out.print("csftp> ");
                // read command input
                try {
                    len = System.in.read(cmdString);
                } catch (IOException exception) {
                    System.err.println("0xFFFE Input error while reading commands, terminating.");
                    closeSocket(socket);
                    System.exit(1);
                }

                if (len <= 0)
                    break;

                // parse command as character string
                command = (new String(Arrays.copyOfRange(cmdString, 0, len-1)));

                // ignore empty lines and lines starting with "#"
                if (command.trim().length() == 0 || (command.length() > 0 && command.indexOf("#") == 0)) {
                    continue;
                }

                // trim trailing whitespace
                command = command.replaceAll("\\s++$", "");

                // Start processing the command here.
                if (command.equals("quit")) {
                    // close the connection
                    sendRequest(out, "QUIT", in);

                    // close the socket
                    closeSocket(socket);

                    // break the command input loop, exit the program
                    break;
                }

                // split the command input to command and arguments
                String[] commandSplit = command.split("\\s+");
                int commandSplitLen = commandSplit.length;

                if (commandSplitLen == 2 && commandSplit[0].equals("user")) {
                    // user USERNAME
                    sendRequest(out, "USER " + commandSplit[1], in);
                } else if (commandSplitLen == 2 && commandSplit[0].equals("pw")) {
                    // pw PASSWORD
                    sendRequest(out, "PASS " + commandSplit[1], in);
                } else if (command.equals("features")) {
                    // features
                    sendRequest(out, "FEAT", in);
                } else if (commandSplitLen == 2 && commandSplit[0].equals("cd")) {
                    // cd DIRECTORY
                    sendRequest(out, "CWD " + commandSplit[1], in);
                } else if (command.equals("dir")) {
                    // dir
                    listDirectory(out, in);
                } else if (commandSplitLen == 2 && commandSplit[0].equals("get")) {
                    // get REMOTE
                    getFile(out, commandSplit[1], in);
                } else if (!isValidCommand(commandSplit[0])) {
                    // the command is not one of the supported commands
                    System.out.println("0x001 Invalid command.");
                } else {
                    // the command is one of the supported commands, but it does not match the usage pattern
                    System.out.println("0x002 Incorrect number of arguments");
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("0xFFFC Control connection to " + hostName + " on port " + hostPort + " failed to open.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
    }

    /**
     * Check if the command is supported (valid)
     *
     * @param command the command inputed
     * @return whether the command is supported
     */
    private static boolean isValidCommand(String command) {
        // compare the input command to all supported commands
        for (String validCommand : validCommands) {
            if (command.equals(validCommand)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Send the command to the server and print the responses
     *
     * @param out the output stream to send the command
     * @param command the command to be sent
     * @param in the input stream to get responses
     */
    private static void sendRequest(PrintWriter out, String command, BufferedReader in) {
        System.out.println(REQUEST_PREFIX + command);

        // send the command
        out.println(command);
        // print the responses
        printResponse(in);
    }

    /**
     * Print all responses from the server
     *
     * @param in the input stream to read responses from
     */
    private static void printResponse(BufferedReader in) {
        try {
            String response;

            // read and print all responses
            while ((response = in.readLine()) != null) {
                System.out.println(RESPONSE_PREFIX + response);
            }
        } catch (SocketTimeoutException e) {
            // stop waiting for more responses
        } catch (IOException e) {
            System.out.println("0xFFFF Processing error. Reading response IO error, terminating.");
            System.exit(1);
        }
    }

    /**
     * List the directory in the server's working directory
     *
     * @param out the output stream to send the LIST command
     * @param in the input stream to get control responses
     */
    private static void listDirectory(PrintWriter out, BufferedReader in) {
        // create the data connection
        if (DataConnection.createDataConnection(out, in)) {
            BufferedReader dataIn = DataConnection.getDataIn();

            // list all the files
            System.out.println(REQUEST_PREFIX + "LIST");
            out.println("LIST");

            // print control responses
            printResponse(in);
            // print data responses
            printResponse(dataIn);

            // close the data connection
            DataConnection.closeDataConnection();
        } else {
            // print control responses if the data connection was not created successfully
            printResponse(in);
        }
    }

    /**
     * Get a file from the server
     *
     * @param out the output stream to send the RETR command
     * @param fileName the filename of the file to get
     * @param in the input stream to get control responses
     */
    private static void getFile(PrintWriter out, String fileName, BufferedReader in) {
        // create the data connection
        if (DataConnection.createDataConnection(out, in)) {
            InputStream dataInputStream = DataConnection.getDataInputStream();

            // get the file
            System.out.println(REQUEST_PREFIX + "RETR " + fileName);
            out.println("RETR " + fileName);

            boolean fileNotFound = false;

            // check if the file requested exist on the server's working directory
            try {
                String response = in.readLine();

                System.out.println(RESPONSE_PREFIX + response);

                if (response.substring(0, 3).equals("550")) {
                    fileNotFound = true;
                }
            } catch (SocketTimeoutException e) {
                // stop waiting for more responses
            } catch (IOException e) {
                System.out.println("0xFFFF Processing error. Reading response IO error, terminating.");
                System.exit(1);
            }

            // print control responses
            printResponse(in);

            // return if the file is not found on the server
            if (fileNotFound) {
                // close the data connection
                DataConnection.closeDataConnection();

                return;
            }

            // get the remote file and write the local file
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(fileName);

                int b;

                // read and write all data
                while ((b = dataInputStream.read()) != -1) {
                    fileOutputStream.write(b);
                }
            } catch (SocketTimeoutException e) {
                // stop waiting for more responses
                System.out.println("0xFFFF Processing error. File may not be completely retrieved, terminating.");
                System.exit(1);
            } catch (IOException e) {
                System.out.println("0x38E Access to local file " + fileName + " denied.");
            } finally {
                // close the file output stream
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        System.out.println("0xFFFF Processing error. Cannot close the file output stream, terminating.");
                        System.exit(1);
                    }
                }
            }

            // print remaining control responses
            printResponse(in);

            // close the data connection
            DataConnection.closeDataConnection();
        } else {
            // print control responses if the data connection was not created successfully
            printResponse(in);
        }
    }

    /**
     * Close the socket
     *
     * @param socket the socket to be closed
     * @return whether the socket is closed successfully
     */
    private static boolean closeSocket(Socket socket) {
        if (socket == null || socket.isClosed()) {
            return true;
        }

        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("0xFFFF Processing error. Cannot close the socket, terminating.");
            System.exit(1);
        } finally {
            return socket.isClosed();
        }
    }


    /**
     * A class for managing FTP data connection
     */
    private static class DataConnection {
        static Socket dataSocket;
        static InputStream dataInputStream;
        static BufferedReader dataIn;

        /**
         * Get the data connection socket
         *
         * @return the data connection socket
         */
        public static Socket getDataSocket() {
            return dataSocket;
        }

        /**
         * Get the data connection raw input stream
         *
         * @return the data connection input stream
         */
        public static InputStream getDataInputStream() {
            return dataInputStream;
        }

        /**
         * Get the reader for the data connection input stream
         *
         * @return the reader for the data connection input stream
         */
        public static BufferedReader getDataIn() {
            return dataIn;
        }

        /**
         * Create a data connection
         *
         * @param out the output stream to send control command
         * @param in the input stream to get control responses
         * @return whether the data connection is created successfully
         */
        public static boolean createDataConnection(PrintWriter out, BufferedReader in) {
            // get the data connection data
            DataConnectionData dataConnectionData = getDataConnectionData(out, in);

            if (dataConnectionData != null) {
                String host = dataConnectionData.getHost();
                int port = dataConnectionData.getPort();

                // the data connection cannot be created if the data connection data could not be parsed successfully
                if (host == null || port == -1) {
                    return false;
                }

                // create the data connection
                try {
                    dataSocket = new Socket(host, port);
                    dataSocket.setSoTimeout(500);
                    dataInputStream = dataSocket.getInputStream();
                    dataIn = new BufferedReader(new InputStreamReader(dataInputStream));
                } catch (UnknownHostException e) {
                    System.out.println("0x3A2 Data transfer connection to " + host + " on port " + port + " failed to open.");
                    System.exit(1);
                } catch (IOException e) {
                    System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                    System.exit(1);
                }

                return true;
            }

            return false;
        }

        /**
         * Get the data connection data
         *
         * @param out the output stream to send the PASV command
         * @param in the input stream to get responses for data connection
         * @return the data connection data
         */
        private static DataConnectionData getDataConnectionData(PrintWriter out, BufferedReader in) {
            System.out.println(REQUEST_PREFIX + "PASV");

            // request a passive data connection
            out.println("PASV");

            // get the response, parse and return it as data connection data
            try {
                String response = in.readLine();

                System.out.println(RESPONSE_PREFIX + response);

                return new DataConnectionData(response);
            } catch (SocketTimeoutException e) {
                // stop waiting for responses
                return null;
            } catch (IOException e) {
                System.out.println("0xFFFF Processing error. Reading response IO error, terminating.");
                System.exit(1);
            }

            return null;
        }

        /**
         * Close the data connection
         *
         * @return whether the data connection is closed successfully
         */
        private static boolean closeDataConnection() {
            if (dataSocket == null || dataSocket.isClosed()) {
                return true;
            }

            // close the data socket and deallocate the resources
            try {
                dataSocket.close();
            } catch (IOException e) {
                System.out.println("0xFFFF Processing error. Cannot close the socket, terminating.");
                System.exit(1);
            } finally {
                boolean socketClosed = dataSocket.isClosed();

                dataSocket = null;
                dataInputStream = null;
                dataIn = null;

                return socketClosed;
            }
        }


        /**
         * A data structure for data connection data
         */
        private static class DataConnectionData {
            String host;
            int port;

            /**
             * Constructor, parse the passive data connection request's response
             *
             * @param passiveRequestResponse the request's response
             */
            public DataConnectionData(String passiveRequestResponse) {
                parseResponse(passiveRequestResponse);
            }

            /**
             * Get the host for the data connection
             *
             * @return the host for data connection
             */
            public String getHost() {
                return host;
            }

            /**
             * Get the port for the data connection
             *
             * @return the port for data connection
             */
            public int getPort() {
                return port;
            }

            /**
             * Parse the passive data connection request's response as host and port
             *
             * @param response the response to be parsed
             */
            private void parseResponse(String response) {
                int openParenthesisIndex = response.indexOf("(");
                int closingParenthesisIndex = response.indexOf(")");

                if (openParenthesisIndex != -1 && closingParenthesisIndex != -1) {
                    String[] hostsPortsNumbers = response.substring(openParenthesisIndex+1, closingParenthesisIndex).split(",");

                    host = hostsPortsNumbers[0] + "." + hostsPortsNumbers[1] + "." + hostsPortsNumbers[2] + "." + hostsPortsNumbers[3];
                    port = Integer.parseInt(hostsPortsNumbers[4]) * 256 + Integer.parseInt(hostsPortsNumbers[5]);
                } else {
                    host = null;
                    port = -1;
                }
            }
        }
    }
}
