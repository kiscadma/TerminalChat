import java.io.Serializable;

public class Message implements Serializable
{
	private static final long serialVersionUID = 1L;
	public String sender;
	public String receiver;
	public String content;
	
	public Message(String sender, String receiver, String content)
	{
		this.sender = sender;
		this.receiver = receiver;
		this.content = content;
	}
}
