/**
Group ID: 1
Yazan Yahya Alshaebi			        ID: 2142647
Mohanad Sulaiman Ali Al Dakheel         ID: 2135847
Ammar Abdulilah Omar Bin Madi	        ID: 2135146
Fahad Adil Alghamdi                     ID: 2135938

 - Hardware
 CPU: AMD Ryzen 5800hs 3.20 GHz
 RAM: 16GB
 OS: Windows 11.0
 IDE: IntelliJ IDEA 2023.1 (Ultimate Edition)
 Runtime version: 17.0.6+10-b829.5 amd64
 VM: OpenJDK 64-Bit Server VM by JetBrains s.r.o.
 **/

import java.io.*;
import java.util.*;
public  class Main {

    //------------------------- Static Variables -------------------------
    private static EntryTLB[] TLB;                    //List of TLB entries
    private static PageTableEntry[] pageTable;        //List of Page table entries
    private static byte[] physicalMemory;            //Representation of physical memory
    private static RandomAccessFile backingStore;    //The file holding storage
    private static ArrayList<Integer> freeFrames;    //List of free frames in memory
    private static ArrayList<Integer> freeSlots;    //List of free slots in TLB
    static int clock = 0;            //Clock used for timestamping
    private static int faultCount = 0;        //The total number of page faults
    private static int hitTLB;            //The number of times the TLB is hit


    //------------------------- Object Classes -------------------------
    private static class EntryTLB {
        public int pageNumber = -1;        //The page number in the slot
        public int frameNumber = -1;    //The frame associated with the given page number
        public int timestamp = -1;        //When the slot was allocated
    }

    private static class PageTableEntry {
        public int frameNumber = -1;    //The frame associated with the given page number
        public int timestamp = -1;        //When the frame was allocated
    }


    //------------------------- Start of Main -------------------------
    public static void main(String[] args) throws IOException {
        Scanner input = new Scanner(System.in);
        System.out.print("Enter 128 or 256 for Physical Memory Size: ");
        int choice = input.nextInt();
        PrintWriter output = new PrintWriter("output.txt");
        TLB = new EntryTLB[16];
        pageTable = new PageTableEntry[256];
        physicalMemory = new byte[256*choice];
        freeFrames = new ArrayList<>();
        freeSlots = new ArrayList<>();

        //Fill arrays with default values
        for(int i = 0; i < TLB.length; i++) {
            TLB[i] = new EntryTLB();
            freeSlots.add(i);
        }
        for(int i = 0; i < pageTable.length; i++) {
            pageTable[i] = new PageTableEntry();
        }
        for(int i = 0; i < choice; i++) {
            freeFrames.add(i);
        }

        backingStore = new RandomAccessFile("BACKING_STORE.bin","r");
        //Open the list of addresses
        File addresses = new File("addresses.txt");
        Scanner addressReader = new Scanner(new FileReader(addresses));

        //For each address, get the proper output
        while(addressReader.hasNextLine()) {
            output.print(generateOutputString(addressReader.nextLine()));
            clock++;
        }
        addressReader.close();
        output.println("Page Faults = " + faultCount);
        output.println("TLB Hits = " + hitTLB);
        output.flush();
        output.close();
    }



    ////------------------------- Generate output String -------------------------
    private static String generateOutputString(String logicalAddress) {
        //Separate the input data into meaningful parts
        int address = Integer.parseInt(logicalAddress);
        int pageNumber = address / 256;
        int offset = address % 256;

        //Generate the physical address based on the logical address
        int frameNumber = getFrameFromTLB(pageNumber);
        int physicalMemoryAddress = (frameNumber * 256) + offset;

        return  String.format("%-21s %-10s %-17s %-10s %-22s %-15s%n", "The logical address:", logicalAddress,
                "Physical address:", physicalMemoryAddress,
                "The signed byte value:", physicalMemory[physicalMemoryAddress]);
    }

    ////------------------------- Get Frame number from the TLB Or Page Number -------------------------
    private static int getFrameFromTLB(int pageNumber) {
        int result = -1;
        for (int i = 0; i < TLB.length && TLB[i].pageNumber != -1; i++) {
            if (TLB[i].pageNumber == pageNumber) {
                result = TLB[i].frameNumber;
                hitTLB++; // Increment the hitTLB counter only when a TLB entry is found
                TLB[i].timestamp = clock; // Update the timestamp of the TLB entry
            }
        }
        //If Not in TLB then Go to Page Table
        if (result == -1) {
            result = getFrameFromPageTable(pageNumber);

            int freeSlot = getFreeTLB();

            TLB[freeSlot].pageNumber = pageNumber;
            TLB[freeSlot].timestamp = clock;
            TLB[freeSlot].frameNumber = result;
        }
        return result;
    }

    ////------------------------- Get Frame number Page Number -------------------------
    private static int getFrameFromPageTable(int pageNumber) {
        int result = pageTable[pageNumber].frameNumber;
        // If the frame is not in the page table, load it in from the backing store
        if (result == -1) { // Update the condition to check for -1
            int freeFrame = getFreeFrame();
            getPageFromBackingStore(pageNumber, freeFrame);
            pageTable[pageNumber].frameNumber = freeFrame;
            pageTable[pageNumber].timestamp = clock;
            faultCount++;

            // Update TLB entry with new frame number
            for (EntryTLB entryTLB : TLB) {
                if (entryTLB.pageNumber == pageNumber) {
                    entryTLB.frameNumber = freeFrame;
                    break;
                }
            }
        }
        result = pageTable[pageNumber].frameNumber;

        return result;
    }

    ////------------------------- Load Pages From Backing store -------------------------
    private static void getPageFromBackingStore(int page, int frame) {
        try {
            backingStore.seek(page * 256L);
            backingStore.read(physicalMemory, frame * 256, 256);
        }
        catch (IOException e){
            System.out.println("Exception, page not in memory");
        }
    }


    ////------------------------- Get a free frame -------------------------
    private static int getFreeFrame() {
        if (freeFrames.isEmpty()) {
            int victim = Integer.MAX_VALUE;
            int victimPos = 0;

            for (int i = 0; i < pageTable.length; i++) {
                if (pageTable[i].timestamp < victim && pageTable[i].timestamp >= 0) {
                    victim = pageTable[i].timestamp;
                    victimPos = i;
                }
            }
            freeFrames.add(pageTable[victimPos].frameNumber);

            pageTable[victimPos].frameNumber = -1;
            pageTable[victimPos].timestamp = -1;

            for (EntryTLB entryTLB : TLB) {
                if (entryTLB.pageNumber == victimPos) { // Compare with victimPos instead of pageTable[victimPos].frameNumber
                    entryTLB.pageNumber = -1;
                    entryTLB.frameNumber = -1;
                    entryTLB.timestamp = -1;
                }
            }
        }
        return freeFrames.remove(0);
    }


    //------------------------- Get a TLB Free Slot -------------------------
    private static int getFreeTLB() {
        if(freeSlots.isEmpty()) {
            EntryTLB victim = TLB[0];
            int victimPos = 0;

            for(int i = 0; i < TLB.length; i++) {
                if(TLB[i].timestamp < victim.timestamp) {
                    victim = TLB[i];
                    victimPos = i;
                }
            }
            freeSlots.add(victimPos);
        }
        //remove first one if full
        return freeSlots.remove(0);
    }
}
