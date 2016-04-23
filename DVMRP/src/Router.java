import tools.IO;

import java.util.List;
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

                for (int attachIndex = 0; attachIndex < obAttachLans.length; attachIndex++) {
                    // prepare DV
                    sb.setLength(0);
                    for (int tLan = 0; tLan < LAN_TOTAL; tLan++)
                    {
                        int distance = obRoutTable[tLan][0];
                        int nextHopRouter = obRoutTable[tLan][1];
                        int nextHopLan = obRoutTable[tLan][2];
                        if (distance > 0 && nextHopLan == obAttachLans[attachIndex])        //last modified
                        {
                            //poison reverse
                            sb.append(LAN_TOTAL + " " + nextHopRouter + " ");
                        }
                        else
                        {
                            sb.append(distance + " " + nextHopRouter + " ");
                        }
                    }

                    IO.instance().write("rout" + obRouterID,
                            "DV " + obAttachLans[attachIndex] + " " + obRouterID + " " + sb.toString());
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
                    List<String> content = IO.instance().read("lan" + obAttachLans[i]);
                    for (String line : content) {
                        String loDataType = line.split(" ")[0];
                        if ("DV".equals(loDataType))
                        {
                            updateTable(line);
                        }
                        else if ("data".equals(loDataType))
                        {
                            multicastData(line);
                        }
                        else if ("receiver".equals(loDataType))
                        {

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
        int attachRouter = Integer.valueOf(parts[2]);
        int attachLan = Integer.valueOf(parts[1]);
        //DV message from self
        if (attachRouter == obRouterID)
        {
            return;
        }

        for (int i = 3; i < parts.length; i += 2)
        {
            int lanID = (i - 3) / 2;
            int distance = Integer.valueOf(parts[i]);
            int nextHopFromNeighborTable = Integer.valueOf(parts[i + 1]);
            if (distance < LAN_TOTAL && distance + 1 <= obRoutTable[lanID][0])
            {
                // if same distance, update only when router id is smaller
                if (distance + 1 == obRoutTable[lanID][0] && attachRouter > obRoutTable[lanID][1])
                {
                    continue;
                }
                obRoutTable[lanID][0] = distance + 1;
                obRoutTable[lanID][1] = attachRouter;
                obRoutTable[lanID][2] = attachLan;
            }
            // update child map by using poison reverse
            else if (distance == LAN_TOTAL && nextHopFromNeighborTable == obRouterID)
            {
                // neighbor router/lan use me as next hop to lanID
                int bit = 1;
                for (int b = 0; b < attachLan; b++)
                {
                    bit <<= 1;
                }
                obRoutTable[lanID][3] |= bit;
            }
        }
    }

    /**
     * receive "data lan-id host-lan-id" from lanX, forward to child lan of hostID (parent lan)
     */
    private static void multicastData(String arContent)
    {
        String[] parts = arContent.split(" ");
        int curLanID = Integer.valueOf(parts[1]);
        int hostLanID = Integer.valueOf(parts[2]);

        // check parent lan
        int parentLan = obRoutTable[hostLanID][2];
        if (parentLan != curLanID)
        {
            return;
        }

        // forward to child lan
        int bitmap = obRoutTable[hostLanID][3];     //last modified
        int childLan = 0;
        while (bitmap > 0)
        {
            if((bitmap & 1) == 1) {
                IO.instance().write("rout" + obRouterID,
                        parts[0] + " " + childLan + " " + hostLanID);
            }
            childLan += 1;
            bitmap >>= 1;
        }
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
