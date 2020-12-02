
import java.util.Map;

public class Group
{
    private String name;
    private Map<Integer, String> members;
    // private Poll poll;

    public Group(String name, Map<Integer, String> members)
    {
        this.name = name;
        this.members = members;
    }

    public String getName()
    {
        return name;
    }

    public Map<Integer, String> getMembers()
    {
        return members;
    }

    public void addMember(String name, int id)
    {
        members.put(id, name);
    }

    public void removeMember(int id)
    {
        members.remove(id);
    }
}
