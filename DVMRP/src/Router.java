import tools.IO;

import java.util.*;

/**
 * Created by xiezebin on 4/20/16.
 */
public class Router {

    private static int obRouterID;
    private static int[] obAttachLans;

    private static int obRoutTable[][];         //lan -> distance -> next hop

    private static Map<Integer, String> obNeighborDV;   //key is routerID
    private static Set<Integer> obLanWithReceivers;

    private final static int LAN_TOTAL = 10;
    private final static long ONE_SECOND = 1000;

    private static Map<Integer, Map<Integer, Set<Integer>>> obSourceRouterMap;    //key1 is hostLanID, key2 is attachLanID, value is child routers
    private static Map<Integer, Map<Integer, Set<Integer>>> obNMRRouters;
    private static Set<Integer> obNonMemOfHostLan;      //save the host_lan_id which this router is not a member
    private static Timer obNMRSendTimer;

//    private static int NonMemReportExpire = 20;
//    private static int MemReportExpire = 20;

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
//                NonMemReportExpire--;
//                MemReportExpire--;

                for (int i = 0; i < obAttachLans.length; i++) {
                    List<String> content = IO.instance().read("lan" + obAttachLans[i]);
                    for (String line : content) {
                        String loDataType = line.split(" ")[0];
                        if ("DV".equals(loDataType))
                        {
                            updateTable(line);

                            int attachRouter = Integer.valueOf(line.split(" ")[2]);
                            if (attachRouter != obRouterID)
                            {
                                obNeighborDV.put(attachRouter, line);
                            }
                        }
                        else if ("data".equals(loDataType))
                        {
                            multicastData(line);
                        }
                        else if ("receiver".equals(loDataType))
                        {
                            // save receiver position,
                            // if this lan is not in bitmap, need to check which of neighbor routers to forward package
                            obLanWithReceivers.add(obAttachLans[i]);
                        }
                        else if ("NMR".equals(loDataType))
                        {
                            handleNonMemReport(line);
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

                // save child router of hostLanID
                if (obSourceRouterMap.get(lanID) == null)
                {
                    obSourceRouterMap.put(lanID, new HashMap<Integer, Set<Integer>>());
                }
                Map<Integer, Set<Integer>> lanToRouters = obSourceRouterMap.get(lanID);
                if (lanToRouters.get(attachLan) == null)
                {
                    lanToRouters.put(attachLan, new HashSet<Integer>());
                }
                Set<Integer> routers = lanToRouters.get(attachLan);

                routers.add(attachRouter);
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
        boolean hasForward = false;
        while (childLan < LAN_TOTAL)
        {
            if((bitmap & 1) == 1) {
                // though bitmap has 1, should check if this lan receive NMR from all child routers
                if (!obSourceRouterMap.get(hostLanID).equals(obNMRRouters.get(hostLanID)))
                {
                    IO.instance().write("rout" + obRouterID,
                            parts[0] + " " + childLan + " " + hostLanID);
                    hasForward = true;
                }
            }
            // if not forward, handle receiver in lan
            if (!hasForward && obLanWithReceivers.contains(childLan))           //last modified
            {
                if (handleReceiverInLan(childLan, hostLanID)) {
                    IO.instance().write("rout" + obRouterID,
                            parts[0] + " " + childLan + " " + hostLanID);
                    hasForward = true;
                }
            }
            childLan += 1;
            bitmap >>= 1;
        }

        // not bitmap and no receiver, the first router to send NMR
        if (!hasForward)
        {
            obNonMemOfHostLan.add(hostLanID);
            sendNonMemReport();     // only first NMR is sent immediately
        }
        else
        {
            obNonMemOfHostLan.remove(hostLanID);
        }
    }

    /**
     * For each destination lan
     * 1. if parent lan is lanId, ignore
     * 2. if myself has minimum hop, should forward package
     * 3. if hops num are same, myself has minimum router id, should forward package
     * @param arLanId
     */
    private static boolean handleReceiverInLan(int arLanId, int arHostLanID)
    {
        // parent lan was current lan, is already in bitmap of parent router
        if (obRoutTable[arHostLanID][2] == arLanId)
        {
            return false;
        }
        // leaf lan, not in any bitmap
        else if (obNeighborDV.isEmpty())
        {
            return true;
        }
        // between two routers, should check with router to forward the multicast
        else
        {
            int curHops = obRoutTable[arHostLanID][0];
            for (Map.Entry<Integer, String> entry: obNeighborDV.entrySet())
            {
                int attachRouter = entry.getKey();
                String loDVmessage = entry.getValue();
                String[] parts = loDVmessage.split(" ");
                int loLanID = Integer.valueOf(parts[1]);

                if (loLanID == arLanId)
                {
                    int distIndex = arHostLanID * 2 + 3;
                    int neighborHops = Integer.valueOf(parts[distIndex]);
                    if (neighborHops < curHops || (neighborHops == curHops && attachRouter < obRouterID))
                    {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /**
     * after received NMR from child router, determine if myselft should send NMR to parent
     * @param arReport
     */
    private static void handleNonMemReport(String arReport)
    {
        String[] parts = arReport.split(" ");
        int attachLanId = Integer.valueOf(parts[1]);
        int routerId = Integer.valueOf(parts[2]);
        int hostLanId = Integer.valueOf(parts[3]);

        // check if the attachRouter is my child router, w.r.t. hostLanID
        String loDVmessage = obNeighborDV.get(routerId);
        if (loDVmessage == null)
        {
            return;
        }
        String[] DVparts = loDVmessage.split(" ");
        int distIndex = hostLanId * 2 + 3;
        int distance = Integer.valueOf(DVparts[distIndex]);
        int nextHopFromNeighborTable = Integer.valueOf(DVparts[distIndex + 1]);
        if (distance == LAN_TOTAL && nextHopFromNeighborTable == obRouterID)
        {
            // is my child router
        }
        else
        {
            return;
        }

        if (routerId != obRouterID)
        {
            if (obNMRRouters.get(hostLanId) == null)
            {
                obNMRRouters.put(hostLanId, new HashMap<Integer, Set<Integer>>());
            }
            Map<Integer, Set<Integer>> lanToRouters = obNMRRouters.get(hostLanId);
            if (lanToRouters.get(attachLanId) == null)
            {
                lanToRouters.put(attachLanId, new HashSet<Integer>());
            }
            Set<Integer> routers = lanToRouters.get(attachLanId);

            routers.add(routerId);
        }
    }
    /**
     * NMR lan-id router-id host-lan-id
     */
    private static void sendNonMemReport()
    {
        if (obNMRSendTimer != null) {
//            obNMRSendTimer.cancel();
//            obNMRSendTimer.purge();
            return;
        }

        obNMRSendTimer = new Timer();
        obNMRSendTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (int hostLanID : obNonMemOfHostLan) {
                    int parentLanID = obRoutTable[hostLanID][2];
                    IO.instance().write("rout" + obRouterID,
                            "NMR " + parentLanID + " " + obRouterID + " " + hostLanID);
                }
            }
        }, 0, ONE_SECOND * 10);
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

        obNeighborDV = new HashMap<Integer, String>();
        obLanWithReceivers = new HashSet<Integer>();

        obSourceRouterMap = new HashMap<Integer, Map<Integer, Set<Integer>>>();
        obNMRRouters = new HashMap<Integer, Map<Integer, Set<Integer>>>();
        obNonMemOfHostLan = new HashSet<Integer>();

        sendDVmessage();
        processDataFromLan();
    }
}
