import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Group
{
    private String name;
    private final int POLL_TIMER_SECONDS = 30;
    private Map<Integer, String> members;
    private Set<Integer> voters;
    private volatile String pollQuestion;
    private int yesVotes;
    private int noVotes;
    private PollTimer pt;
    private Server serv;

    public Group(String name, Map<Integer, String> members, Server serv)
    {
        this.name = name;
        this.members = members;
        this.pollQuestion = "";
        this.serv = serv;
        voters = Collections.synchronizedSet(new HashSet<>());
    }

    public void addPoll(String question)
    {
        pollQuestion = question;
        serv.addMessage(new Message("SERVER", name, POLL_TIMER_SECONDS + " second poll created: " + pollQuestion));
        pt = new PollTimer();
        pt.start();
    }

    public String getPollQuestion()
    {
        return pollQuestion;
    }

    public boolean voteOnPoll(boolean yes, int id)
    {
        if (voters.contains(id)) return false;

        if (yes) yesVotes++;
        else noVotes++;

        voters.add(id);
        if (yesVotes + noVotes == members.size()) pt.stop();
        
        return true;
    }

    public String getName()
    {
        return name;
    }

    public int getYesVotes()
    {
        return yesVotes;
    }

    public int getNoVotes()
    {
        return noVotes;
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

    private class PollTimer implements Runnable
    {
        private Thread t;

        public void start()
        {
            t = new Thread(this);
            t.start();
        }

        public void stop()
        {
            t.interrupt();
        }

        public void run()
        {
            try
            {
                Thread.sleep(POLL_TIMER_SECONDS*1000);
            } 
            catch (InterruptedException e) {}

            serv.finishPoll(name, "The poll '" + pollQuestion + "' ended. Yes [ " + yesVotes + " ] / No [ " + noVotes + " ].");
            pollQuestion = "";
            yesVotes = 0;
            noVotes = 0;
            voters.clear();
        }
    }
}
