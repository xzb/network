import tools.IO;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by xiezebin on 4/20/16.
 */
public class Router {

    private static int obRouterID;
    private static int[] obAttachLans;

    private static int obRoutTable[][];        //lan -> distance -> next hop

    private final static int LAN_TOTAL = 10;
    private final static long ONE_SECOND = 1000;


    /**
     * Every 5 seconds, each router will send a distance vector message to each of its LAN
     * DV lan-id router-id d0 router0 d1 router1 d2 router2 . . . d9 router9
     */
    private static void sendDVmessage()
    {
        Timer loSendTimer = new Timer();
        loSendTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();

                for (int attachLan = 0; attachLan < obAttachLans.length; attachLan++) {
                    // prepare DV
                    sb.setLength(0);
                    for (int tLan = 0; tLan < LAN_TOTAL; tLan++)
                    {
                        int distance = obRoutTable[tLan][0];
                        int nextHopRouter = obRoutTable[tLan][1];
                        int nextHopLan = obRoutTable[tLan][2];
                        if (distance > 0 && nextHopLan == attachLan)
                        {
                            //poison reverse
                            sb.append(LAN_TOTAL + " " + nextHopRouter + " ");
                        }
                        else
                        {
                            sb.append(distance + " " + nextHopRouter + " ");
                        }
                    }

                    IO.instance().write("lan" + obAttachLans[attachLan],
                            "DV " + obAttachLans[attachLan] + " " + obRouterID + " " + sb.toString());
                }
            }
        }, 0, ONE_SECOND * 5);
    }

    /**
     * read data from lan every second, react as updateTable or multicastData
     */
    private static void processDataFromLan()
    {
        Timer loCheckTimer = new Timer();
        loCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (int i = 0; i < obAttachLans.length; i++) {
                    String content = IO.instance().read("lan" + obAttachLans[i]);   //TODO: should read till end
                    if (content != null) {
                        String loDataType = content.split(" ")[0];
                        if ("data".equals(loDataType))
                        {
                            multicastData(content);
                        }
                        else if ("DV".equals(loDataType))
                        {
                            updateTable(content);
                        }
                    }
                }

            }
        }, ONE_SECOND, ONE_SECOND);
    }

    /**
     * receive DV message from lanX, update routTable
     * DV lan-id router-id d0 router0 d1 router1 d2 router2 . . . d9 router9
     */
    private static void updateTable(String arDVmessage)
    {
        String[] parts = arDVmessage.split(" ");
        int nextHopRouter = Integer.valueOf(parts[2]);
        int nextHopLan = Integer.valueOf(parts[1]);

        for (int i = 3; i < parts.length; i += 2)
        {
            int lanID = (i - 3) / 2;
            int distance = Integer.valueOf(parts[i]);
            if (distance < LAN_TOTAL && distance + 1 < obRoutTable[lanID][0])
            {
                obRoutTable[lanID][0] = distance + 1;
                obRoutTable[lanID][1] = nextHopRouter;
                obRoutTable[lanID][2] = nextHopLan;
            }
        }
    }

    /**
     * receive "data lan-id host-lan-id" from lanX, forward to child lan of hostID (parent lan)
     */
    private static void multicastData(String arContent)
    {

    }

    /**
     * @param args router-id lan-ID lan-ID lan-ID ...
     */
    public static void main(String[] args)
    {
        if (args.length < 2)
        {
            System.out.println("router arguments error.");
            System.exit(1);
        }
        obRouterID = Integer.valueOf(args[0]);

        obRoutTable = new int[LAN_TOTAL][5];    //distance, next router, parent lan, child lan map, leaf lan map
        for (int i = 0; i < 10; i++)
        {
            obRoutTable[i][0] = LAN_TOTAL;     //initial infinite distance
            obRoutTable[i][1] = -1;
            obRoutTable[i][2] = -1;
        }

        int lanNum = args.length - 1;
        obAttachLans = new int[lanNum];
        for (int i = 0; i < lanNum; i++)
        {
            int lanID = Integer.valueOf(args[i + 1]);
            obAttachLans[i] = lanID;
            obRoutTable[lanID][0] = 0;
            obRoutTable[lanID][1] = obRouterID;
            obRoutTable[lanID][2] = lanID;
        }

        sendDVmessage();
        processDataFromLan();
    }
}
