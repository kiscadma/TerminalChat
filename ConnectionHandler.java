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
			if      (command.equalsIgnoreCase("connect")) handleConnect();
			else if (command.equalsIgnoreCase("message")) handleMessage();
			else if (command.equalsIgnoreCase("disconnect")) handleDisconnect();
			else if (command.equalsIgnoreCase("creategroup")) handleCreateGroup();
			else if (command.equalsIgnoreCase("poll")) handlePoll();
			else if (command.equalsIgnoreCase("addtogroup")) handleAddToGroup();
			else if (command.equalsIgnoreCase("leavegroup")) handleLeaveGroup();
			else if (command.equalsIgnoreCase("mygroups"))
				serv.addMessage(new Message("SERVER",userName,"Your groups: " + serv.getGroupsForUser(userName).toString()));
			else if (command.equalsIgnoreCase("listmembers")) handleListMembers();
		} 
		catch (ClassNotFoundException e)
		{ 
		}
		catch (IOException e)
		{
			handleDisconnect();
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
			
		}
	}

	private void handleMessage()
	{
		try
		{
			Message m = (Message) in.readObject();
			out.writeObject("message");
			out.writeObject(m);
			serv.addMessage(m);
		} 
		catch (ClassNotFoundException | IOException e)
		{
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
		}
	}

	private void handlePoll()
	{
		try
		{
			String groupName = (String) in.readObject();

			// make sure the group exists
			if (!serv.getGroupNames().contains(groupName))
			{
				serv.addMessage(new Message("SERVER", userName, "There is no group with the name '" + groupName + "'"));
				return;
			}

			String msg = ((String) in.readObject()).toLowerCase().trim();
			boolean isValid;
			if (msg.equals("yes") || msg.equals("no")) // this must be a poll vote
			{
				isValid = serv.voteOnPoll(groupName, msg.equals("yes"), id);
				if (!isValid) 
					serv.addMessage(new Message("SERVER", userName, "Unable to vote on a poll for the " + groupName + " group."));
			} 
			else // it is a poll question
			{
				isValid = serv.createPoll(groupName, msg, id);
				if (!isValid) 
					serv.addMessage(new Message("SERVER", userName, "Unable to create a poll for the " + groupName + " group."));
			}			
		}
		catch (ClassNotFoundException | IOException e)
		{
		}
	}

	private void handleListMembers()
	{
		try
		{
			String groupName = (String) in.readObject();

			// make sure the group exists
			if (!serv.getGroupNames().contains(groupName))
			{
				serv.addMessage(new Message("SERVER", userName, "There is no group with the name '" + groupName + "'"));
				return;
			}

			// make sure the user is in the group
			if (!serv.getGroup(groupName).contains(userName))
			{
				serv.addMessage(new Message("SERVER", userName, "You are not permitted to see the members of the '" + groupName + "' group"));
				return;
			}

			serv.addMessage(new Message("SERVER", userName, "Members of " + groupName + ": " + serv.getGroup(groupName).toString()) );
		}
		catch (ClassNotFoundException | IOException e)
		{
		}
	}

	private void handleAddToGroup()
	{
		try
		{
			String groupName = (String) in.readObject();

			// make sure the gorup exists
			if (!serv.getGroupNames().contains(groupName))
			{
				serv.addMessage(new Message("SERVER", userName, "There is no group with the name '" + groupName + "'"));
				return;
			}

			String newMemberName = (String) in.readObject();

			serv.addUserToGroup(id, userName, groupName, newMemberName);
		}
		catch (ClassNotFoundException | IOException e)
		{
		}
	}

	private void handleLeaveGroup()
	{
		try
		{
			String groupName = (String) in.readObject();

			// make sure the gorup exists
			if (!serv.getGroupNames().contains(groupName))
			{
				serv.addMessage(new Message("SERVER", userName, "There is no group with the name '" + groupName + "'"));
				return;
			}

			serv.leaveGroup(id, userName, groupName);
		}
		catch (ClassNotFoundException | IOException e)
		{
		}
	}

	private boolean checkName(String name) throws IOException
	{
		if (serv.getConnectedUsers().contains(name) || serv.getGroupNames().contains(name) || name.equals("server")) 
		{
			out.writeObject("message");
			out.writeObject(new Message("SERVER", userName,
				"The name '" + name +"' is unavailable. Please try again.\n"));
			return false;
		}
		else if (name.charAt(0) == '$')
		{
			out.writeObject("message");
			out.writeObject(new Message("SERVER", userName,
				"Usernames are not allowed to begin with the '$' character.\n"));
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
		ms.stopSending();
		try
		{
			out.writeObject("message");
			out.writeObject(new Message("SERVER", userName, "The server is shutting down. Have a nice day!"));
			out.writeObject("disconnect");
			out.flush();
			in.close();
			out.close();
		} catch (IOException e) {
		}
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
						// check for the official server shutdown message
						if (m.sender.equals("[all] SERVER") && m.content.equals("SHUTDOWN"))
						{
							keepRunning = false;
							continue; // continue so we don't send this internal server message to client
						}
						out.writeObject("message");
						out.writeObject(m);
					}
				}
				stop();
				msgThread = null;
			}
			catch (IOException | InterruptedException e)
			{
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
		
		public void stopSending()
		{
			keepRunning = false;
			if (msgThread != null) msgThread.interrupt();
		}
	}
}
