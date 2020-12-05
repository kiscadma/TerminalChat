import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Client implements Runnable
{
	private volatile boolean keepRunning;
    private Thread controlThread;
	private Socket s;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private BufferedReader keyboard;
	private String name;
	private MessageReceiver mr;

    public Client(String name, String host, int port) throws IOException
    {
		this.name = name;
        s = new Socket(host, port);
        out = new ObjectOutputStream(s.getOutputStream());
        in = new ObjectInputStream(s.getInputStream());
		mr = new MessageReceiver();
	}
	
	public void run()
    {
        try
        {
            // connect to the server automatically and start receiver
            connect(name);
            mr.start();

			keyboard = new BufferedReader(new InputStreamReader(System.in));
			String[] lineArr;
			String command, line;
			
            do
            {
				System.out.print("\n> "); System.out.flush();
				line = keyboard.readLine();	
				lineArr = line.split(" ");

                command = lineArr[0];
				if      (command.toLowerCase().equals("disconnect")) disconnect();
				else if (command.toLowerCase().equals("connect")) connect(lineArr[1]);
                else if (command.toLowerCase().equals("msg")) sendMessage(lineArr);
				else if (command.toLowerCase().equals("creategroup")) createGroup(lineArr);
				else if (command.toLowerCase().equals("poll")) poll(lineArr);
            } while (keepRunning);
        }
        catch (IOException e)
        {
			e.printStackTrace();
        }
	}
	
	private void poll(String[] lineArr) throws IOException
	{
		String msg = "";
        for (int i = 2; i < lineArr.length; i++) msg += " "+lineArr[i];
		out.writeObject("poll");
		out.writeObject(lineArr[1]); // groupname
		out.writeObject(msg.toLowerCase().trim());
	}

	private void sendMessage(String[] lineArr)
	{
		try
		{
            String receiver = lineArr[1];
            String content = "";
            for (int i = 2; i < lineArr.length; i++) content += " "+lineArr[i];
			Message m = new Message(name, receiver, content.trim());
			out.writeObject("message");
			out.writeObject(m);
		}
		catch (Exception e)
		{
			System.out.println("please use the following message format:\nmsg [receiver's name] [message content]");
		}
    }

    private void createGroup(String[] lineArr)
	{
		try
		{
            String groupName = lineArr[1];
            String members = "";
            for (int i = 2; i < lineArr.length; i++) members += " "+lineArr[i];
            out.writeObject("createGroup");
            out.writeObject(groupName);
			out.writeObject(members);
		}
		catch (Exception e)
		{
			System.out.println("please use the following message format:\n"
				+ "creategroup [group name] [group members delimited by spaces]");
		}
    }

    private void disconnect() throws IOException
	{
        out.writeObject("disconnect");
	}

	private void connect(String userName) throws IOException
	{
        out.writeObject("connect");
		out.writeObject(userName.toLowerCase());
		name = userName;
	}

    public void start()
    {
        if (controlThread == null)
        {
            keepRunning = true;
            controlThread = new Thread(this);
            controlThread.start();
        }
    }

	private class MessageReceiver implements Runnable
	{
		private Thread controlThread;
		
		private void receiveMessage()
		{
			try
			{
				Message m = (Message) in.readObject();
				
				// remove the "> " above
				System.out.print(String.format("\033[%dA", 1)); // Move up 1 line
				System.out.print("\033[2K"); // Erase line content
				
				System.out.print("\n> " + m.sender + ": " + m.content + "\n\n> ");
				System.out.flush();
			}
			catch (ClassNotFoundException | IOException e)
			{
				// ignore for now
			}
		}
		
		public void run()
		{
			String command;
			while (keepRunning)
			{
				try
				{
					command = (String) in.readObject();
                    if (command.equals("message")) receiveMessage();
					else if (command.equals("disconnect")) stop();
				}
				catch (ClassNotFoundException | IOException e)
				{
					// ignore for now
				}
			}
		}
		
		public void start()
		{
			if (controlThread == null)
			{
				keepRunning = true;
				controlThread = new Thread(this);
				controlThread.start();
			}		
		}
		
		public void stop()
		{
			keepRunning = false;
		}
	}

    public static void main(String[] args) throws IOException
	{
		// get command line args
		String name = (args.length > 0) ? args[0].toLowerCase() : System.getProperty("user.name");
		String host = (args.length > 1) ? args[1] : "localhost"; 
		int    port = (args.length > 2) ? Integer.parseInt(args[2]) : 5045; //Server.PORT;
        
        Client c = new Client(name, host, port);
        c.start();
	}
}
