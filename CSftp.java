
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
        Socket dataSocket = createDataConnection(out, in);
    }

    private static Socket createDataConnection(PrintWriter out, BufferedReader in) {
        DataConnectionData dataConnectionData = getDataConnectionData(out, in);

        if (dataConnectionData != null) {
            String host = dataConnectionData.getHost();
            int port = dataConnectionData.getPort();

            try {
                return new Socket(host, port);
            } catch (UnknownHostException e) {
                System.out.println("0x3A2 Data transfer connection to " + host + " on port " + port + " failed to open.");
            } catch (IOException e) {
                System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
            }
        } else {
            return null;
        }

        return null;
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

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("0xFFFF Processing error. Cannot close the socket, terminating.");
            // System.exit(1);
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
