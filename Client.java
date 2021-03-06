import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Client class for a TerminalChat user.
 *
 * Authors: Mitchell Kiscadden and Zeru Tadesse
 **/
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
	private String defaultSendTo;
	private Map<String, String> aliasMap;
    public Client(String name, String host, int port) throws IOException
    {
		this.name = name;
        s = new Socket(host, port);
        out = new ObjectOutputStream(s.getOutputStream());
        in = new ObjectInputStream(s.getInputStream());
		mr = new MessageReceiver();
		defaultSendTo = "all";
		aliasMap = new HashMap<String, String>();
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

				// check for alias usage
				for (int i = 1; i < lineArr.length; i++)
					if (lineArr[i].charAt(0) == '$' && aliasMap.containsKey(lineArr[i])) 
						lineArr[i] =  aliasMap.get(lineArr[i]);

				command = lineArr[0];
				if (command.equalsIgnoreCase("disconnect") || command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("q"))
					disconnect();
				else if (command.equalsIgnoreCase("connect") || command.equalsIgnoreCase("c"))
					connect(lineArr[1]);
				else if (command.equalsIgnoreCase("msg") || command.equalsIgnoreCase("m"))
					sendMessage(lineArr,false);
				else if (command.equalsIgnoreCase("reply") || command.equalsIgnoreCase("r"))
					sendMessage(lineArr, true);
				else if (command.equalsIgnoreCase("creategroup") || command.equalsIgnoreCase("cg"))
					createGroup(lineArr);
				else if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("h") || command.equalsIgnoreCase("commands"))
					displayHelp();
				else if (command.toLowerCase().equals("poll")) 
					poll(lineArr);
				else if (command.equalsIgnoreCase("list"))
					getUserList(lineArr);
				else if (command.equalsIgnoreCase("mygroups"))
					getMyGroups();
				else if (command.equalsIgnoreCase("alias"))
					setAlias(lineArr);
				else if (command.equalsIgnoreCase("addtogroup")){
					addtogroup(lineArr);
				}else if (command.equalsIgnoreCase("leavegroup")) {
					leaveGroup(lineArr);
				}
				else
					System.out.println("Try 'help'");
            } while (keepRunning);
        }
        catch (IOException e)
        {
			e.printStackTrace();
			disconnect();
        }
	}

	private void getUserList(String[] group) throws IOException
	{
		out.writeObject("listmembers");
		if (group.length < 2) out.writeObject("all");
		else out.writeObject(group[1]);
	}

	private void getMyGroups() throws IOException
	{
		out.writeObject("mygroups");
	}

	private void addtogroup(String[] line)throws IOException{
		if (line.length >= 3){
			out.writeObject("addtogroup");
			out.writeObject(line[1]);
			out.writeObject(line[2]);
		}else{
			displayHelp();
		}
	}

	private void leaveGroup(String[] line)throws IOException
	{
		if (line.length < 2) {
			displayHelp();
			return;
		}
		out.writeObject("leavegroup");
		out.writeObject(line[1]);
	}

	private void setAlias(String[] line)
	{
		if (line.length < 3)
		{
			displayHelp();
			return;
		}

		String actualName = line[1];
		String alias = "$" + line[2];

		aliasMap.put(alias, actualName);
		System.out.println("\n> alias set for '" + actualName + "' - that word can now be replaced with " + alias);
	}

	private void poll(String[] lineArr) throws IOException
	{
		if (lineArr.length < 3)
		{
			displayHelp();
			return;
		}

		String msg = "";
        for (int i = 2; i < lineArr.length; i++) msg += " "+lineArr[i];
		out.writeObject("poll");
		out.writeObject(lineArr[1]); // groupname
		out.writeObject(msg.toLowerCase().trim());
	}

	private void displayHelp() {
		System.out.println("\t\t\t\u001B[35m***Terminal Chat Help Page***"+"\u001B[0m");
		System.out.println("\tSupported Commands:");
		System.out.printf("\t%-40s %s\n", "disconnect ", "Disconnect from the server");
		System.out.printf("\t%-40s %s\n", "connect [name] ", "Connect with a new name");

		System.out.printf("\t%-40s %s\n", "msg [user] [message] ", "Send a message to a user");
		System.out.printf("\t%-40s %s\n", "msg all [message]  ", "Send a message to everyone");
		System.out.printf("\t%-40s %s\n", "msg [group name] [message]", "Send a group message");
		System.out.printf("\t%-40s %s\n", "reply [message]", "reply to the last person you were in contact with");
		System.out.printf("\t%-40s %s\n", "creategroup [group name] [user] ...", "Create a group with users ");
		System.out.printf("\t%-40s %s\n", "addtogroup [group name] [user]", "Add a user to a group ");
		System.out.printf("\t%-40s %s\n", "leavegroup [group name] ", "Remove yourself from a group ");
		System.out.printf("\t%-40s %s\n", "list [groupname] ", "Display users in this group");
		System.out.printf("\t%-40s %s\n", "mygroups ", "Display groups you are a part of");
		System.out.printf("\t%-40s %s\n", "poll [group name] [question]", "Create a poll for a group");
		System.out.printf("\t%-40s %s\n", "poll all [question]", "Create a poll for all");
		System.out.printf("\t%-40s %s\n", "poll [group name] [yes/no]", "Vote yes/no on a poll for a group");
		System.out.printf("\t%-40s %s\n", "poll all [yes/no]", "Vote yes/no on a poll for the group of");
		System.out.printf("\t%-40s %s\n", " ", "all currently connected users");

		System.out.printf("\t%-40s %s\n", "alias [name] [alias]", "Set an alias for a user/word.");
		System.out.printf("\t%-40s %s\n", " ", "The word can be replaced with $[alias]");
		System.out.printf("\t%-40s %s\n", "help ", "Display this help page");

		System.out.printf("\n\t%-40s\n", "Keyboard input persists through incoming messages. If a message is received"); 
		System.out.printf("\t%-40s\n", "and displayed on top of your message, your original input can still be edited"
						  + "\n\tand sent.");
	}

	private void sendMessage(String[] lineArr, boolean reply)
	{
		try
		{
			int i = 1;
			if (!reply){
				defaultSendTo = lineArr[1];
				i ++;
			}
            String content = "";
            for (; i < lineArr.length; i++) content += " "+lineArr[i];
			Message m = new Message(name, defaultSendTo, content.trim());
			out.writeObject("message");
			out.writeObject(m);
		}
		catch (Exception e)
		{
			System.out.println("Unable to send message. Use the 'help' command for information on how to send messages.");
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
			System.out.println("Unable to create group. Use the 'help' command for information on how to create groups.");
		}
    }

    private void disconnect()
	{
		try
		{
			out.writeObject("disconnect");
			keepRunning = false;
		}
		catch (IOException e)
		{
			System.exit(-1);
		}
        
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
		private volatile boolean keepReceiving;
		
		private void receiveMessage()
		{
			try
			{
				Message m = (Message) in.readObject();
			
				System.out.print("\033[2K"); // Erase typing content
				System.out.print(String.format("\033[%dA", 1)); // Move up 1 line
				System.out.print("\033[2K"); // Erase newline above current line
				
				if (m.sender.split(" ")[m.sender.split(" ").length-1].equals("SERVER")) // from SERVER
				{ 
					System.out.print("\n> \u001B[43m\u001B[30m" + m.sender + "\u001B[0m: " + m.content + "\n\n> ");
				} 
				else if (m.sender.contains("[")) //Group
				{ 
					defaultSendTo = m.sender.split("\\[")[1].split("\\]")[0];
					System.out.print("\n> \u001B[42m\u001B[30m" + m.sender + "\u001B[0m: " + m.content + "\n\n> ");
				}
				else if (m.sender.equals(name)) //message rebounding to user for confirmation it was sent to server
				{
					System.out.print("\n> \u001B[46m\u001B[30m" + m.sender + "->" + m.receiver + "\u001B[0m: " + m.content + "\n\n> ");
				}
				else //Private message from someone else
				{
					defaultSendTo = m.sender; //.split(" ")[m.sender.split(" ").length-1]
					System.out.print("\n> \u001B[45m\u001B[30m" + m.sender + "\u001B[0m: " + m.content + "\n\n> ");
				}
				
				System.out.flush();
			}
			catch (ClassNotFoundException | IOException e)
			{
			}
		}
		
		public void run()
		{
			String command;
			while (keepReceiving)
			{
				try
				{
					command = (String) in.readObject();
                    if (command.equals("message")) receiveMessage();
					else if (command.equals("disconnect")) stop();
				}
				catch (ClassNotFoundException | IOException e)
				{
				}
			}
		}
		
		public void start()
		{
			if (controlThread == null)
			{
				keepReceiving = true;
				controlThread = new Thread(this);
				controlThread.start();
			}		
		}
		
		public void stop()
		{
			keepReceiving = false;
			System.exit(0);
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
