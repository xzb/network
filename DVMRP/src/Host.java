import tools.IO;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by xiezebin on 4/20/16.
 */
public class Host
{
    private static String obHostID;
    private static String obLanID;
    private static String obType;
    private static long obTimeToStart;
    private static long obPeriod;

    private static String obHoutFile;

    private final static long ONE_SECOND = 1000;

    /**
     * If host is receiver, send report every 10 seconds
     * Format: receiver lan-id
     */
    private static void receiverReport()
    {
        Timer loTimer = new Timer();
        loTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                IO.instance().write(obHoutFile,
                        "receiver " + obLanID);
            }
        }, 0, ONE_SECOND * 10);
    }

    /**
     * If host is receiver, check new data in lanX, copy to hostX, for every 1 second
     */
    private static void checkNewData()
    {
        Timer loCheckTimer = new Timer();
        loCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String content = IO.instance().read("lan" + obLanID);
                if (content != null)
                {
                    IO.instance().write(obHoutFile,
                            content);
                }
            }
        }, ONE_SECOND, ONE_SECOND);

    }

    /**
     * If host is sender, send data every period
     * Format: data lan-id host-lan-id
     */
    private static void sendData()
    {
        Timer loSendTimer = new Timer();
        loSendTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                IO.instance().write(obHoutFile,
                        "data " + obLanID + " " + obHostID);
            }
        }, obTimeToStart, obPeriod);

    }

    /**
     * @param args host-id lan-id type time-to-start period
     */
    public static void main(String[] args)
    {
        if (args.length < 3)
        {
            System.out.println("host arguments error.");
            System.exit(1);
        }
        obHostID = args[0];
        obLanID = args[1];
        obType = args[2];
        obHoutFile = "hout" + obHostID;

        if ("receiver".equals(obType.toLowerCase()))
        {
            receiverReport();
            checkNewData();
        }
        else if ("sender".equals(obType.toLowerCase()))
        {
            obTimeToStart = Long.valueOf(args[3]) * ONE_SECOND;
            obPeriod = Long.valueOf(args[4]) * ONE_SECOND;
            sendData();
        }

    }
}
