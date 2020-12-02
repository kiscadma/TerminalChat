import java.util.List;

public class Group
{
    private String name;
    private List<String> memberNames;
    private List<Integer> memberIDs;
    // private Poll poll;

    public Group(String name, List<String> memberNames, List<Integer> memberIDs)
    {
        this.name = name;
        this.memberNames = memberNames;
        this.memberIDs = memberIDs;
    }

    public String getName()
    {
        return name;
    }

    public List<String> getMemberNames()
    {
        return memberNames;
    }

    public List<Integer> getMemberIDs()
    {
        return memberIDs;
    }

    public void addMember(String name, int id)
    {
        memberNames.add(name);
        memberIDs.add(id);
    }

    public void removeMember(int id)
    {
        int index = memberIDs.indexOf(id);
        memberNames.remove(index);
        memberNames.remove(index);
    }
}
