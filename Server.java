import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Server implements Runnable
{
	public static final int PORT = 46200;
	private volatile boolean keepRunning;
    private Map<String, Integer> userIDs; // userName : id
    private Map<String, Group> groups; // groupName : Group
	private Map<Integer, List<Message>> messages; // id : [messages intended for them]
	private Thread controlThread;
	private ServerSocket ss;
	private int userID = 0;
	
	public Server()
	{
        userIDs = Collections.synchronizedMap(new HashMap<String, Integer>());
        groups = Collections.synchronizedMap(new HashMap<String, Group>());
		messages = Collections.synchronizedMap(new HashMap<Integer, List<Message>>());
		addGroup("all", new LinkedList<String>());
	}
	
	public void addUser(String userName, ConnectionHandler ch, int userID)
	{
        // they haven't connected or been mentioned before
        if (!userIDs.containsKey(userName))
        {
            userIDs.put(userName, userID);
			messages.put(userID, new LinkedList<Message>());
            System.out.println("SERVER: added new user " + userName + "[id:" + userID + "]");
        }
        else // they are a returning user or have already been messaged
        {
            userID = userIDs.get(userName);
            ch.setID(userID); // update the connectionhandler's id
            System.out.println("SERVER: adding existing user " + userName + "[id:" + userID + "]");
		}
	
		// add them to the 'all' group if they are actually online
		if (ch != null)
		{
			System.out.println("SERVER: adding " + userName + " to 'all' group");
			groups.get("all").addMember(userName, userID);
		}
    }

    public void addGroup(String groupName, List<String> memberNames)
    {
		Map<Integer, String> members = Collections.synchronizedMap(new HashMap<>());

		// add members to members map
		for (String name : memberNames) 
		{
			// if a user doesn't exist yet, add them
			if (!userIDs.containsKey(name)) addUser(name, null, userID++);
			members.put(userIDs.get(name), name);

			// SERVER will notify people that they've been added to the group. first member name is creator
			addMessage(new Message("SERVER", name, 
						"you were added to the " + groupName + " group by " + memberNames.get(0)));
		}
        groups.put(groupName, new Group(groupName, members));
		System.out.println("SERVER: created group " + groupName + " with " 
			+ memberNames.size() + " members: " + memberNames);
    }
    
    public void removeUser(int userID)
    {
		String name = groups.get("all").getMembers().get(userID);
		groups.get("all").removeMember(userID);
		System.out.println("SERVER: user:" + name + " has disconnected. They have been removed from 'all'");
    }
	
	public void addMessage(Message m)
	{
		String recipient = m.receiver;
		String sender = m.sender;

        // check if this is a group message
        if (groups.containsKey(recipient))
        {
			Group g = groups.get(recipient);

			// users can only message groups that they are in. SERVER can send to anyone
			if (!sender.equals("SERVER") && !g.getMembers().containsKey(userIDs.get(sender)))
			{
				addMessage(new Message("SERVER", sender, 
						"you do not have permission to message the " + recipient + " group."));
				return;
			}

			// recipient is the groupName
			m.sender = "[" + recipient + "] " + m.sender;
			Map<Integer, String> members = g.getMembers();
			
			// iterate through group members, messaging everyone except the sender
			for (Integer id : members.keySet())
			{
				if (members.get(id).equals(sender)) continue;
				messages.get(id).add(m);
			}
			System.out.println("SERVER: " + sender + " messaged the '" + recipient + "' group: " 
				+ g.getMembers().values());
        }
        else // not a group message. let's add the message to the user's list
        {
            // we don't know this reciever. let's add them
            if (!userIDs.containsKey(recipient)) addUser(recipient, null, userID++);

            int receiverID = userIDs.get(recipient);
			messages.get(receiverID).add(m);
			System.out.println("SERVER: " + m.sender + " messaged " + recipient);
        }
	}
	
	public List<Message> getMessagesForUser(int userID)
	{
        LinkedList<Message> msgs = new LinkedList<>();
        if (!messages.get(userID).isEmpty()) 
        {
            msgs.addAll(messages.get(userID));
            messages.get(userID).clear(); // clear the messages because we're about to send them
        }
		return msgs;
	}

	public List<String> getConnectedUsers()
	{
		return new LinkedList<>(groups.get("all").getMembers().values());
	}

	public List<String> getGroupNames()
	{
		return new LinkedList<>(groups.keySet());
	}

	public void run()
	{
		try
		{
			Socket s;
			ss = new ServerSocket(PORT);
			ss.setSoTimeout(3000000);
			
			while (keepRunning)
			{
				s = ss.accept();
				System.out.println("SERVER: accepted a connection");
                ConnectionHandler ch = new ConnectionHandler(s, this, userID++);
				ch.start();
			}
			controlThread = null;
		} 
		catch (IOException e)
		{
			e.printStackTrace();
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
		// send remaining messages and do other stopping stuff
	}

	public static void main(String[] args)
	{
		Server s = new Server();
		s.start();
	}
}
