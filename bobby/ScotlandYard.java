package bobby;

import java.net.*;
import java.io.*;
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
                    try
                    {
                    socket = server.accept();
                    }
                    catch(SocketTimeoutException ignored)
                    {
                        continue;
                    }
                    this.board.threadInfoProtector.acquire();
                    if(socket!=null)
                        this.board.dead = false; //>
                    this.board.threadInfoProtector.release();
                    if(socket != null)
                    {
                        fugitiveIn = true;
                    }
                    //fugitiveIn=true;
                } while (!fugitiveIn);
                assert server != null;
                System.out.println(this.gamenumber);

                // Spawn a thread to run the Fugitive
                Runnable  tau1 =  new ServerThread(board,  -1,  socket,  port,  gamenumber);
                this.board.threadInfoProtector.acquire();
                this.board.totalThreads += 1; //?
                this.board.playingThreads += 1;
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
                        else
                         break;    
                    }
                    assert socket1 != null;
					//System.out.println("wdcwr");
					
					/*
					acquire thread info lock, and decide whether you can serve the connection at this moment,

					if you can't, drop connection (game full, game dead), continue, or break.

					if you can, spawn a thread, assign an ID, increment the totalThreads

					don't forget to release lock when done!
					*/
                    ///System.out.println("crcefecf1");
                    this.board.threadInfoProtector.acquire();
                    //System.out.println("crcefecf2");
                    int k = this.board.getAvailableID() ;
                    if(k!= -1 && !this.board.dead) {

                        int new_id = k;
                        Runnable tau3 =  new ServerThread(board,  new_id,  socket1,  port,  gamenumber);
                        threadPool.execute(tau3);
                        this.board.totalThreads += 1;
                        //System.out.println("crcefecf");

                    }
                    else if(k== -1 && !this.board.dead){
                        continue;
                    }
                    else if(k == -1 && this.board.dead){
                        break;
                    }
                    this.board.threadInfoProtector.release();
                }
                /*
				reap the moderator thread, close the server,

				kill threadPool (Careless Whispers BGM stops)
				*/
                System.out.println("exiting ..1");
                mod.join();
                if(server!=null)
                    server.close();
                System.out.println("exiting ..2");    
                threadPool.shutdown();

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