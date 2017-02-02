
import java.lang.System;
import java.io.IOException;

import java.io.*;
import java.net.*;

import java.util.Arrays;

//
// This is an implementation of a simplified version of a command
// line ftp client. The program always takes two arguments
//


public class CSftp {
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;

    static final String REQUEST_PREFIX = "--> ";
    static final String RESPONSE_PREFIX = "<-- ";

    public static void main(String [] args) {
        byte cmdString[] = new byte[MAX_LEN];
        String command;

        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
            // then exit.

        if (args.length != ARG_CNT) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        String hostName = args[0];
        int hostPort = Integer.parseInt(args[1]);

        try (
            // create a socket
            Socket socket = new Socket(hostName, hostPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            // set socket timeout for reading responses
            socket.setSoTimeout(500);

            printResponse(in);

            try {
                for (int len = 1; len > 0;) {
                    System.out.print("csftp> ");
                    len = System.in.read(cmdString);

                    command = (new String(Arrays.copyOfRange(cmdString, 0, len-1)));

                    if (len <= 0)
                        break;

                    // Start processing the command here.
                    if (command.equals("quit")) {
                        // close the connection
                        sendRequest(out, "QUIT", in);

                        // close the socket
                        closeSocket(socket);

                        // break the command input loop, exit the program
                        break;
                    }

                    if (command.split(" ").length == 2 && command.split(" ")[0].equals("user")) {
                        // user USERNAME
                        sendRequest(out, "USER " + command.split(" ")[1], in);
                    } else if (command.split(" ").length == 2 && command.split(" ")[0].equals("pw")) {
                        // pw PASSWORD
                        sendRequest(out, "PASS " + command.split(" ")[1], in);
                    } else if (command.equals("features")) {
                        // features
                        sendRequest(out, "FEAT", in);
                    } else if (command.split(" ").length == 2 && command.split(" ")[0].equals("cd")) {
                        // cd DIRECTORY
                        sendRequest(out, "CWD " + command.split(" ")[1], in);
                    } else if (command.equals("dir")) {
                        // dir
                        listDirectory(out, in);
                    } else if (command.split(" ").length == 2 && command.split(" ")[0].equals("get")) {
                        // get REMOTE
                        getFile(out, command.split(" ")[1], in);
                    } else {
                        System.out.println("900 Invalid command.");
                    }
                }
            } catch (IOException exception) {
                System.err.println("998 Input error while reading commands, terminating.");
            }
        } catch (UnknownHostException e) {
            System.err.println("0xFFFC Control connection to " + hostName + " on port " + hostPort + " failed to open.");
            // System.exit(1);
        } catch (IOException e) {
            System.err.println("0xFFFD Control connection I/O error, closing control connection.");
            // System.exit(1);
        }
    }

    private static void sendRequest(PrintWriter out, String command, BufferedReader in) {
        System.out.println(REQUEST_PREFIX + command);

        out.println(command);
        printResponse(in);
    }

    private static void printResponse(BufferedReader in) {
        try {
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println(RESPONSE_PREFIX + response);
            }
        } catch (SocketTimeoutException e) {
            // stop waiting for more responses
        } catch (IOException e) {
            // TODO: print error message
            System.out.println("0xFFFF Processing error.");
        }
    }

    private static void listDirectory(PrintWriter out, BufferedReader in) {
        // create the data connection
        DataConnection.createDataConnection(out, in);

        BufferedReader dataIn = DataConnection.getDataIn();

        // list all the files
        System.out.println(REQUEST_PREFIX + "LIST");
        out.println("LIST");

        // print all responses
        printResponse(in);
        printResponse(dataIn);

        // close the data connection
        DataConnection.closeDataConnection();
    }

    private static void getFile(PrintWriter out, String fileName, BufferedReader in) {
        // create the data connection
        DataConnection.createDataConnection(out, in);

        InputStream dataInputStream = DataConnection.getDataInputStream();

        // get the file
        System.out.println(REQUEST_PREFIX + "RETR " + fileName);
        out.println("RETR " + fileName);

        // print all responses
        printResponse(in);

        // write the file
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(fileName);

            int b;

            while ((b = dataInputStream.read()) != -1) {
                fileOutputStream.write(b);
            }
        } catch (IOException e) {
            // TODO: print error message
            System.out.println("0xFFFF Processing error.");
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    System.out.println("0xFFFF Processing error. Cannot close the file output stream, terminating.");
                }
            }
        }

        // print all remaining control responses
        printResponse(in);

        // close the data connection
        DataConnection.closeDataConnection();
    }

    private static boolean closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("0xFFFF Processing error. Cannot close the socket, terminating.");
            // System.exit(1);
        } finally {
            return socket.isClosed();
        }
    }


    private static class DataConnection {
        static Socket dataSocket;
        static InputStream dataInputStream;
        static BufferedReader dataIn;

        public static Socket getDataSocket() {
            return dataSocket;
        }

        public static InputStream getDataInputStream() {
            return dataInputStream;
        }

        public static BufferedReader getDataIn() {
            return dataIn;
        }

        public static void createDataConnection(PrintWriter out, BufferedReader in) {
            DataConnectionData dataConnectionData = getDataConnectionData(out, in);

            if (dataConnectionData != null) {
                String host = dataConnectionData.getHost();
                int port = dataConnectionData.getPort();

                try {
                    dataSocket = new Socket(host, port);
                    dataSocket.setSoTimeout(500);
                    dataInputStream = dataSocket.getInputStream();
                    dataIn = new BufferedReader(new InputStreamReader(dataInputStream));
                } catch (UnknownHostException e) {
                    System.out.println("0x3A2 Data transfer connection to " + host + " on port " + port + " failed to open.");
                } catch (IOException e) {
                    System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                }
            }
        }

        private static DataConnectionData getDataConnectionData(PrintWriter out, BufferedReader in) {
            System.out.println(REQUEST_PREFIX + "PASV");

            out.println("PASV");

            try {
                String response = in.readLine();

                System.out.println(RESPONSE_PREFIX + response);

                return new DataConnectionData(response);
            } catch (SocketTimeoutException e) {
                // stop waiting for responses
                return null;
            } catch (IOException e) {
                // TODO: print error message
                System.out.println("0xFFFF Processing error.");
            }

            return null;
        }

        private static boolean closeDataConnection() {
            try {
                dataSocket.close();
            } catch (IOException e) {
                System.out.println("0xFFFF Processing error. Cannot close the socket, terminating.");
                // System.exit(1);
            } finally {
                boolean socketClosed = dataSocket.isClosed();

                dataSocket = null;
                dataIn = null;

                return socketClosed;
            }
        }


        private static class DataConnectionData {
            String host;
            int port;

            public DataConnectionData(String passiveRequestResponse) {
                parseResponse(passiveRequestResponse);
            }

            public String getHost() {
                return host;
            }

            public int getPort() {
                return port;
            }

            private void parseResponse(String response) {
                int openParenthesisIndex = response.indexOf("(");
                int closingParenthesisIndex = response.indexOf(")");
                String[] hostsPortsNumbers = response.substring(openParenthesisIndex+1, closingParenthesisIndex).split(",");

                host = hostsPortsNumbers[0] + "." + hostsPortsNumbers[1] + "." + hostsPortsNumbers[2] + "." + hostsPortsNumbers[3];
                port = Integer.parseInt(hostsPortsNumbers[4]) * 256 + Integer.parseInt(hostsPortsNumbers[5]);
            }
        }
    }
}
