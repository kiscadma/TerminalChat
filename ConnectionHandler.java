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

	// TODO: more functionality for checking name validity and notifying client
	// TODO: validate user isn't using group name
	// TODO:  > only message groups that include the sender
	// TODO: add poll
	// TODO: add/remove user from group
	// TODO: fix issue where disconnecting a second time breaks stuff
	// TODO: case insensitivity

	// command to see who is online
	// remove the addUser if they don't exist when being messaged feature? -> add notification
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
        while (keepRunning) readFromClient();
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
			if (serv.getConnectedUsers().contains(userName)) 
			{
				out.writeObject("message");
				out.writeObject(new Message("SERVER", userName, "That username is already being used."
					+ " Please try again with a new name:\nconnect newUsername"));
				return;
			}

			// add the user
			serv.addUser(userName, this, id);

			// tell the user who is online
			serv.addMessage(new Message("SERVER", userName, 
					"Currently online: " + serv.getConnectedUsers().toString()));

			// notify online users that this user connected using the 'all' group
			serv.addMessage(new Message("SERVER", "all", 
			userName + " has entered the chat"));
			
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
			// notify online users that this user is disconnecting using the 'all' group
			serv.addMessage(new Message("SERVER", "all", 
					userName + " has left the chat"));

			serv.removeUser(id);
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
			List<String> members = new LinkedList<>();
			for (String m : ((String) in.readObject()).trim().split(" ")) members.add(m);
			serv.addGroup(groupName, members);
		}
		catch (ClassNotFoundException | IOException e)
		{
			// ignore for now
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
		ms.stop();
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
                    Thread.sleep(1000); // wait before sending anything
                    List<Message> msgs = serv.getMessagesForUser(id);
                    if (msgs.isEmpty()) continue;
					for (Message m : msgs)
					{
						out.writeObject("message");
						out.writeObject(m);
					}
				}
				out.close();
			}
			catch (InterruptedException | IOException e)
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
			msgThread.interrupt();
		}
	}
}
