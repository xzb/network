import tools.IO;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by xiezebin on 4/20/16.
 */
public class Controller
{
    private static String[] obHosts;
    private static String[] obRouters;
    private static String[] obLans;

    private final static long ONE_SECOND = 1000;

    private static void initData(String[] args)
    {
        List<String> loIDLs = new LinkedList<String>();
        for (int i = 0; i < args.length; )
        {
            if ("host".equals(args[i]))
            {
                while (++i < args.length && !"router".equals(args[i]) && !"lan".equals(args[i]))
                {
                    loIDLs.add(args[i]);
                }
                obHosts = new String[loIDLs.size()];
                loIDLs.toArray(obHosts);
            }
            else if ("router".equals(args[i]))
            {
                while (++i < args.length && !"host".equals(args[i]) && !"lan".equals(args[i]))
                {
                    loIDLs.add(args[i]);
                }
                obRouters = new String[loIDLs.size()];
                loIDLs.toArray(obRouters);
            }
            else if ("lan".equals(args[i]))
            {
                while (++i < args.length && !"host".equals(args[i]) && !"router".equals(args[i]))
                {
                    loIDLs.add(args[i]);
                }
                obLans = new String[loIDLs.size()];
                loIDLs.toArray(obLans);
            }
            else
            {
                i++;
            }
            loIDLs.clear();
        }
    }


    /**
     * ONCE EVERY SECOND, check the out file of each host/router, copy (APPEND) new message to the corresponding file of the LAN
     */
    private static void transferDataToLan()
    {
        Timer loCheckTimer = new Timer();
        loCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // transfer message from houtX to lanX
                for (int i = 0; i < obHosts.length; i++) {
                    List<String> content = IO.instance().read("hout" + obHosts[i]);   //TODO: should read till end
                    for(String line : content) {
                        String destLanID = line.split(" ")[1];
                        IO.instance().write("lan" + destLanID, line);
                    }
                }

                // transfer message from routX to lanX
                for (int i = 0; i < obRouters.length; i++) {
                    List<String> content = IO.instance().read("rout" + obRouters[i]);
                    for (String line : content) {
                        String destLanID = line.split(" ")[1];
                        IO.instance().write("lan" + destLanID, line);
                    }
                }
            }
        }, ONE_SECOND, ONE_SECOND);
    }

    /**
     *
     * @param args "host" id id...id "router" id id...id "lan" id id...id
     */
    public static void main(String[] args)
    {
        if (args.length < 3)
        {
            System.out.println("controller arguments error.");
            System.exit(1);
        }

        initData(args);

        transferDataToLan();

    }
}
