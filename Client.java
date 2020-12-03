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

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String[] line;
            String command;

            do
            {
                line = in.readLine().split(" ");
                command = line[0];
				if      (command.toLowerCase().equals("disconnect")) disconnect();
				else if (command.toLowerCase().equals("connect")) connect(line[1]);
                else if (command.toLowerCase().equals("msg")) sendMessage(line);
                else if (command.toLowerCase().equals("creategroup")) createGroup(line);
            } while (keepRunning);
        }
        catch (IOException e)
        {
            // ignore for now
        }
    }

	private void sendMessage(String[] line)
	{
		try
		{
            String receiver = line[1];
            String content = "";
            for (int i = 2; i < line.length; i++) content += " "+line[i];
			Message m = new Message(name, receiver, content.trim());
			out.writeObject("message");
			out.writeObject(m);
			System.out.print("\n> ");
		}
		catch (Exception e)
		{
			System.out.println("please use the following message format:\nmsg [receiver's name] [message content]");
		}
    }

    private void createGroup(String[] line)
	{
		try
		{
            String groupName = line[1];
            String members = "";
            for (int i = 2; i < line.length; i++) members += " "+line[i];
            out.writeObject("createGroup");
            out.writeObject(groupName);
			out.writeObject(members);
		}
		catch (Exception e)
		{
			System.out.println("please use the following message format:\nmessage [receiver's name] [message content]");
		}
    }

    private void disconnect() throws IOException
	{
        out.writeObject("disconnect");
        keepRunning = false;
		mr.stop();
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
				System.out.print("\n\n> "+m.sender+": " + m.content +"\n\n> ");
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
		int    port = (args.length > 2) ? Integer.parseInt(args[2]) : 46200; //Server.PORT;
        
        Client c = new Client(name, host, port);
        c.start();
	}
}
