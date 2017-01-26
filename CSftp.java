
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

                        // TODO: check if the connection is closed

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
        System.out.println(command);

        out.println(command);
        printResponse(in);
    }

    private static void printResponse(BufferedReader in) {
        try {
            // TODO: print all responses
            System.out.println(in.readLine());
        } catch (IOException e) {
            // TODO: print error message
            System.out.println("0xFFFF Processing error.");
        }
    }
}
