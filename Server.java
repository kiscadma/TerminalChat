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
		groups.put("all", new Group("all", new LinkedList<String>(), new LinkedList<Integer>()));
	}
	
	public void addUser(String userName, ConnectionHandler ch, int userID)
	{
        // check if they're new
        if (!userIDs.containsKey(userName))
        {
            userIDs.put(userName, userID);
			messages.put(userID, new LinkedList<Message>());
			groups.get("all").addMember(userName, userID);
            System.out.println("SERVER: added new user " + userName + "[id:" + userID + "]");
        }
        else // they are a returning user or have already been messaged
        {
            int oldID = userIDs.get(userName);
            ch.setID(oldID); // update the connectionhandler's id
            System.out.println("SERVER: adding existing user " + userName + "[id:" + oldID + "]");
        }
    }

    public void addGroup(String groupName, List<String> members)
    {
		LinkedList<Integer> ids = new LinkedList<>();
		for (String name : members) 
		{
			if (!userIDs.containsKey(name)) addUser(name, null, userID++);
			ids.add(userIDs.get(name));
			addMessage(new Message("SERVER", name, "you were added to the " + groupName + " group"));
		}
        groups.put(groupName, new Group(groupName, members, ids));
        System.out.print("SERVER: created group " + groupName + " with members:");
        for (String s : members) System.out.print(" " + s);
        System.out.println();
    }
    
    public void removeUser(int userID)
    {
		groups.get("all").removeMember(userID);
        System.out.println("SERVER: user:" + userID + " has disconnected");
    }
	
	public void addMessage(Message m)
	{
		String recipient = m.receiver;
		String sender = m.sender;

        // check if this is a group message
        if (groups.containsKey(recipient))
        {
			m.sender = "[" + recipient + "] " + m.sender;
			List<Integer> ids = groups.get(recipient).getMemberIDs();
			List<String> names = groups.get(recipient).getMemberNames();
			for (Integer id : ids) if (id != userIDs.get(sender)) messages.get(id).add(m);

            System.out.print("SERVER: " + sender + " messaged the '" + recipient + "' group: ");
			for (String name : names) if (!name.equals(sender)) System.out.print(name + " ");
            System.out.println();
        }
        else // not a group message. let's add the message to the user's list
        {
            // we don't have this reciever online. let's add them
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

	public void run()
	{
		try
		{
			Socket s;
			ss = new ServerSocket(PORT);
			ss.setSoTimeout(300000);
			
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
