package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ScotlandYard implements Runnable {

	/*
		this is a wrapper class for the game.
		It just loops, and runs game after game
	*/

    public int port;
    public int gamenumber;

    public ScotlandYard(int port) {
        this.port = port;
        this.gamenumber = 0;
    }

    public void run() {
        while (true) {
            Thread tau = new Thread(new ScotlandYardGame(this.port, this.gamenumber));
            tau.start();
            try {
                tau.join();
            } catch (InterruptedException e) {
                return;
            }
            this.gamenumber++;
        }
    }

    public class ScotlandYardGame implements Runnable {
        private Board board;
        private ServerSocket server;
        public int port;
        public int gamenumber;
        private ExecutorService threadPool;

        public ScotlandYardGame(int port, int gamenumber) {
            this.port = port;
            this.board = new Board();
            this.gamenumber = gamenumber;
            try {
                this.server = new ServerSocket(port);
                System.out.println(String.format("Game %d:%d on", port, gamenumber));
                server.setSoTimeout(5000);
            } catch (IOException i) {
                return;
            }
            this.threadPool = Executors.newFixedThreadPool(10);
        }


        public void run() {

            try {

                //INITIALISATION: get the game going


                Socket socket = null;
                boolean fugitiveIn = false;
				
				/*
				listen for a client to play fugitive, and spawn the moderator.
				
				here, it is actually ok to edit this.board.dead, because the game hasn't begun
				*/

                do {
                    socket = server.accept();
                    this.board.threadInfoProtector.acquire();
                    this.board.dead = true; //>
                    this.board.threadInfoProtector.release();
                    if(socket != null)
                    {
                        fugitiveIn = true;
                    }
                    //fugitiveIn=true;
                } while (!fugitiveIn);

                System.out.println(this.gamenumber);

                // Spawn a thread to run the Fugitive
                Runnable  tau1 =  new ServerThread(board,  -1,  socket,  port,  gamenumber);
                this.board.threadInfoProtector.acquire();
                this.board.totalThreads += 1; //?
                this.board.threadInfoProtector.release();
                threadPool.execute(tau1);

                // Spawn the moderator
                Thread mod = new Thread(new Moderator(board));
                mod.start();



                while (true) {
					/*
					listen on the server, accept connections
					if there is a timeout, check that the game is still going on, and then listen again!
					*/
                    Socket socket1 = null;

                    try {
                        socket1 = server.accept();

                    } catch (SocketTimeoutException t) {
                        if (!this.board.dead)
                            continue;
                    }
					
					
					/*
					acquire thread info lock, and decide whether you can serve the connection at this moment,

					if you can't, drop connection (game full, game dead), continue, or break.

					if you can, spawn a thread, assign an ID, increment the totalThreads

					don't forget to release lock when done!
					*/
                    this.board.threadInfoProtector.acquire();
                    if(this.board.getAvailableID() != -1 && !this.board.dead) {
                        int new_id = this.board.getAvailableID();
                        Runnable tau3 =  new ServerThread(board,  new_id,  socket1,  port,  gamenumber);
                        threadPool.execute(tau3);
                        this.board.totalThreads += 1;

                    }
                    else if(this.board.getAvailableID() == -1 && !this.board.dead){
                        continue;
                    }
                    else if(this.board.getAvailableID() == -1 && this.board.dead){
                        break;
                    }
                    this.board.threadInfoProtector.release();

				/*
				reap the moderator thread, close the server, 
				
				kill threadPool (Careless Whispers BGM stops)
				*/
                    mod.join();
                    server.close();
                    threadPool.shutdown();

                }

                System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
                return;
            }
            catch (InterruptedException ex){
                System.err.println("An InterruptedException was caught: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            catch (IOException i){
                return;
            }

        }


    }

    public static void main(String[] args) {
        for (int i=0; i<args.length; i++){
            int port = Integer.parseInt(args[i]);
            Thread tau = new Thread(new ScotlandYard(port));
            tau.start();
        }
    }
}