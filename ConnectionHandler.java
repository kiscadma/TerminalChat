import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

/**
 * ConnectionHandler class. This is created using a socket connection from
 * a server. This will sit in the run method reading commands and handling
 * them. There is a MessageSender inner class that will get the list of 
 * outstanding messages for this user and send them every 1000ms.
 */
public class ConnectionHandler implements Runnable
{
	private volatile boolean keepRunning;
	private volatile int id; // this can change so we'll keep it volatile
	private String userName;
	private MessageSender ms;
	private Thread controlThread;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Server serv;

	// TODO: add poll
	// TODO: add/remove user from group
	// TODO: help feature in client

	// command to see who is online
	// add notification saying that a user is offline when you try to message them
	// saving stuff to file to stop and restart
	// welcome messages and logging
	
	public ConnectionHandler(Socket sock, Server serv, int id) throws IOException
	{
		this.serv = serv;
        this.id = id;
        out = new ObjectOutputStream(sock.getOutputStream());
        in  = new ObjectInputStream(sock.getInputStream());
		ms = new MessageSender();
    }
    
    public void setID(int id)
    {
        this.id = id;
    }
	
	public void run()
	{
		while (keepRunning) 
		{
			try
			{
				// we can wait for a little bit to prevent user spam of server
				// and also save the unecessary calls to readFromClient every ms
				Thread.sleep(500);
				readFromClient();
			}
			catch (InterruptedException e){}
		}
        controlThread = null;
	}
	
	private void readFromClient()
	{
		try
		{
			String command = (String) in.readObject();
			if      (command.toLowerCase().equals("connect")) handleConnect();
			else if (command.toLowerCase().equals("message")) handleMessage();
			else if (command.toLowerCase().equals("disconnect")) handleDisconnect();
			else if (command.toLowerCase().equals("creategroup")) handleCreateGroup();
		} 
		catch (ClassNotFoundException | IOException e)
		{
			// IGNORE
		}
	}
	
	private void handleConnect()
	{
		try
		{
			userName = ((String) in.readObject()).toLowerCase();

			// need a unique username
			if (!checkName(userName)) return;

			// notify online users that this user connected using the 'all' group
			serv.addMessage(new Message("SERVER", "all", 
			userName + " has entered the chat"));

			// add the user
			serv.addUser(userName, this, id);

			// welcome/tell the user who is online
			serv.addMessage(new Message("SERVER", userName, 
					"Welcome to TerminalChat! Currently online: " 
					+ serv.getConnectedUsers().toString()));
			
			ms.start(); // we can start the sender after the user is added
		}
		catch (ClassNotFoundException | IOException e)
		{
			System.out.println("Did not receive userName from user"); System.exit(0);
		}
	}

	private void handleMessage()
	{
		try
		{
			Message m = (Message) in.readObject();
			serv.addMessage(m);
		} 
		catch (ClassNotFoundException | IOException e)
		{
			System.out.println("Did not receive userName from user"); System.exit(0);
		}
	}

	private void handleDisconnect()
	{
		try
		{
			serv.removeUser(id);

			// notify online users that this user is disconnecting using the 'all' group
			serv.addMessage(new Message("SERVER", "all", 
					userName + " has left the chat"));

			out.writeObject("disconnect"); // echo disconnect back to the user
			in.close();
			stop();
		}
		catch (IOException e)
		{
			// ignore
		}
	}
	
	private void handleCreateGroup()
	{
		try
		{
			String groupName = (String) in.readObject();

			// need a unique groupName
			if (!checkName(groupName)) return;

			List<String> members = new LinkedList<>();

			// add members to the group. make sure the creator is the first person listed
			members.add(userName);
			for (String m : ((String) in.readObject()).trim().split(" ")) 
				if (!m.equals(userName)) members.add(m);
			serv.addGroup(groupName, members);
		}
		catch (ClassNotFoundException | IOException e)
		{
			// ignore for now
		}
	}

	private boolean checkName(String name) throws IOException
	{
		// Will return true if the name is available, false otherwise. This
		// will also message the user, telling them to try with another name
		// Ideally, we would have a list of reserved names (including server now) 
		// that we would test these names against.
		if (serv.getConnectedUsers().contains(name) || serv.getGroupNames().contains(name) 
			|| name.equals("server")) 
		{
			out.writeObject("message");
			out.writeObject(new Message("SERVER", userName,
				"The name '" + name +"' is unavailable. Please try again.\n"));
			return false;
		}
		return true;
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
		ms.stop();
		try
		{
			in.close();
			out.close();
		} 
		catch (IOException e) {}
	}

	private class MessageSender implements Runnable
	{
		private Thread msgThread;
		private volatile boolean keepRunning;
		
		public void run()
		{
			try
			{
				while (keepRunning)
				{
                    Thread.sleep(500); // wait so that we don't spam access to messages map
                    List<Message> msgs = serv.getMessagesForUser(id);
                    if (msgs.isEmpty()) continue;
					for (Message m : msgs)
					{
						out.writeObject("message");
						out.writeObject(m);
					}
				}
				msgThread = null;
			}
			catch (IOException | InterruptedException e)
			{
				// ignore for now
			} 
		}
		
		public void start()
		{
			if (msgThread == null)
			{
				keepRunning = true;
				msgThread = new Thread(this);
				msgThread.start();
			}
		}
		
		public void stop()
		{
			keepRunning = false;
			if (msgThread != null) msgThread.interrupt();
		}
	}
}
