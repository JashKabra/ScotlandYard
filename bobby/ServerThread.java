package bobby;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


public class ServerThread implements Runnable {
    private Board board;
    private int id;
    private boolean registered;
    private BufferedReader input;
    private PrintWriter output;
    private Socket socket;
    private int port;
    private int gamenumber;

    public ServerThread(Board board, int id, Socket socket, int port, int gamenumber) {

        this.board = board;

        //id from 0 to 4 means detective, -1 means fugitive
        this.id = id;

        this.registered = false;

        this.socket = socket;
        this.port = port;
        this.gamenumber = gamenumber;
    }

    public void run() {

        try {

			/*
			PART 0_________________________________
			Set the sockets up
			*/

            try {

                this.output = new PrintWriter(this.socket.getOutputStream(),true);
                this.input = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

                if (this.id == -1) {
                    output.println(String.format(
                            "Welcome. You play Fugitive in Game %d:%d. You start on square 42. Make a move, and wait for feedback",
                            this.port, this.gamenumber));
                } else {
                    output.println(String.format(
                            "Welcome. You play Detective %d in Game %d:%d. You start on square 0. Make a move, and wait for feedback",
                            this.id, this.port, this.gamenumber));
                }
            } catch (IOException i) {
                this.board.threadInfoProtector.acquire();
                this.board.totalThreads -= 1;
                this.board.erasePlayer(id);
                if(id == -1)
                    board.dead = true;
                this.board.threadInfoProtector.release();
                return;
            }
            //__________________________________________________________________________________________
            while (true) 
            {
                //System.out.println("thread inf loop start");
                boolean quit;
                boolean client_quit = false;
                boolean quit_while_reading = false;
                int target = -1;

                if (this.id == -1 && !this.registered) {
                    board.registration.acquire();
                    registered = true;
                    board.threadInfoProtector.acquire();
                    board.installPlayer(id);
                    board.moderatorEnabler.release();
                    System.out.println("Mod permits(should be 0) " + board.moderatorEnabler.availablePermits());
                    board.threadInfoProtector.release();
                    continue;
                }


                String cmd = null;
                try {
                    if(socket!=null)
                        cmd = input.readLine();
                } catch (IOException i) {
                    if(id!=-1)
                        client_quit = true;
                    else
                        quit_while_reading = true;
                    board.threadInfoProtector.acquire();
                    if(client_quit)
                        board.erasePlayer(id);
                    if(socket!=null)
                    socket.close();
                    board.threadInfoProtector.release();
                }

                if (cmd == null) {

                    if(id!=-1)
                        client_quit = true;
                    else
                        quit_while_reading = true;

                    // release everything socket related
                    board.threadInfoProtector.acquire();
                    if(client_quit)
                        board.erasePlayer(id);
                    //board.quitThreads += 1;
                    if(socket!=null)
                        socket.close();
                    board.threadInfoProtector.release();

                } else if (cmd.equals("Q")) {
                    // client wants to disconnect, set flags
                    if(id!=-1)
                        client_quit = true;
                    else
                        quit_while_reading = true;
                    // release everything socket related
                    board.threadInfoProtector.acquire();
                    if(client_quit)
                        board.erasePlayer(id);
                    //board.quitThreads += 1;
                    if(socket!=null)
                        socket.close();
                    board.threadInfoProtector.release();
                } else {
                    try {
                        target = Integer.parseInt(cmd);

                    } catch (Exception ignored) {
                    }
                }

				/*
				PART 2______________________
				*/
                board.reentry.acquire();
                if (!this.registered) {
                    board.registration.acquire();
                    board.threadInfoProtector.acquire();
                    registered = true;
                    board.installPlayer(id);
                    board.threadInfoProtector.release();
                    if(board.dead)
                    {
                        if(id!=-1)
                            client_quit = true;
                        else
                            quit_while_reading = true;
                        board.threadInfoProtector.acquire();
                        if(client_quit)
                            board.erasePlayer(id);
                        if(socket!=null)
                            socket.close();
                        board.threadInfoProtector.release();
                    }

                }

				
				/*
				_______________________________________________________________________________________
				PART 3___________________________________
				*/
                assert board.threadInfoProtector.availablePermits() == 10;
                quit = client_quit || quit_while_reading;
                board.threadInfoProtector.acquire();
                assert board.threadInfoProtector.availablePermits() == 0;
                if(!quit)
                {
                    if(id==-1) {
                        board.moveFugitive(target);

                    }
                    else {
                        board.moveDetective(id, target);
                    }

                }
                else if(client_quit)
                {
                    board.erasePlayer(id);
                }
                board.threadInfoProtector.release();              
 			
				/*
				PART 4_____________________________________________
				cyclic barrier, first part

				*/
                board.countProtector.acquire();
                    board.count++;
                board.countProtector.release();

                if(board.count == board.playingThreads)
                    board.barrier1.release(board.count);
                board.barrier1.acquire();

                
                System.out.println("barrier1 crossed");
                                        
				/*
				________________________________________________________________________________________

				PART 5_______________________________________________
				get the State of the game, and process accordingly. 

				recall that you can only do this if you're not walking away, you took that
				decision in PARTS 1 and 2

				It is here that everyone can detect if the game is over in this round, and decide to quit
				*/

                if (!client_quit) {
                    String feedback;
                    if(id==-1)
                        feedback = board.showFugitive();
                    else
                        feedback = board.showDetective(id);
                    //pass this to the client via the socket output
                    try {
                        output.println(feedback);
                    }
                    //in case of IO Exception, off with the thread
                    catch (Exception i) {
                        //set flags
                        if(id!=-1)
                            client_quit = true;
                        else
                            quit_while_reading = true;
                        board.threadInfoProtector.acquire();
                        // If you are a Fugitive you can't edit the board, but you can set dead to true
                        if (this.id == -1) {
                            board.dead =true;
                        }
                        if(client_quit)
                            board.erasePlayer(id);
                        if(socket!=null)
                        socket.close();
                        board.threadInfoProtector.release();
                    }

                    boolean indicator = feedback.contains("Play");

                    if (!indicator) {
                        if(id!=-1)
                            client_quit = true;
                        else
                            quit_while_reading = true;

                        // If you are a Fugitive you can't edit the board, but you can set dead to true
                        if (this.id == -1) {
                            board.threadInfoProtector.acquire();
                            board.dead =true;
                            board.threadInfoProtector.release();
                        }
                        // release everything socket related
                        board.threadInfoProtector.acquire();
                        if(client_quit)
                            board.erasePlayer(id);
                        if(socket!=null)
                        socket.close();
                        board.threadInfoProtector.release();
                    }
                }

				/*
				__________________________________________________________________________________
				PART 6A____________________________
				*/
                quit = client_quit||quit_while_reading;
                boolean exited = false;

                if(quit)
                {
                    exited =true;
                    board.threadInfoProtector.acquire();
                    board.totalThreads -= 1;
                    board.quitThreads += 1;
                    board.threadInfoProtector.release();
                    if(socket!=null)
                        socket.close();
                }

				/*
				__________________________________________________________________________________
				PART 6B______________________________
				*/

                boolean i_m_last = false;
                board.countProtector.acquire();
                board.count--;
                board.countProtector.release();
                System.out.println("count before barrier2 : "+ board.count);
                if(board.count == 0 ) {
                    i_m_last = true;
                    System.out.println("yayyy");
                    board.barrier2.release(board.playingThreads);
                }
                board.barrier2.acquire();
                System.out.println("barrier2 crossed");

				/*
				PART 6C_________________________________
				*/
                if(quit_while_reading)
                {
                    board.threadInfoProtector.acquire();
                    board.dead = true;
                    board.erasePlayer(id);
                    board.threadInfoProtector.release();
                }
                if(i_m_last)
                    board.moderatorEnabler.release();
                if(socket==null || client_quit || quit_while_reading || board.dead)
                {
                    assert exited;
                    break;
                }    

                
            }
        }
        catch (AssertionError err) {
            System.out.println("Assert Error");
            err.printStackTrace();
        }
        catch (InterruptedException | IOException ignored) {
            ignored.printStackTrace();
        }
    }


}