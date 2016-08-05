import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.io.FileWriter;

import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import java.time.Instant;
import java.time.Clock;

/**
 *
 * @author Tyler
 */
public class BufferManager {
    
    /**
     * @param args the command line arguments
     */
    
    private static int blockSize=0;
    private static int bufferSize=0;
    private static boolean randomMode = false;
    private static TreeMap[] buffer;
    private static boolean[] notChecked;
    private static Instant[] timestamps;
    
    private static int accessCount = 0;
    
    public static void main(String[] args){
        Scanner in=init(args);
        String query=null;
        String[] sList;
        Scanner objectIn;
        String[] projectionList;
        
        while (in.hasNext() && (query==null || !query.toLowerCase().equals("exit"))){
            query = in.nextLine();
    	
            sList = queryFrom(query);
        
            objectIn=openFile(sList);
    	
            sList = queryWhere(query);
        
            projectionList = querySelect(query);
        
            processQuery(projectionList,objectIn,sList);
            accessCount=0;
            
        }
    }
    
    /**
     * Processes command line arguments
     * @param args command line arguments
     * @return A file scanner or a System.in scanner
     */
    private static Scanner init(String[] args){
    	Scanner in = new Scanner(System.in);
        
    	try{
            if (args.length<3){ //No arguments
                throw new Exception(); //Tell user usage and exit
            }
            else if (args.length<5){ //3 or 2 arguments
                bufferSize=Integer.parseInt(args[0]); //Set buffer size. If args[0] is not an int, exit
                blockSize=Integer.parseInt(args[1]);
                randomMode=args[2].equals("random");
                
                buffer = new TreeMap[bufferSize];
                notChecked = new boolean[bufferSize];
                timestamps = new Instant[bufferSize];
                
                if (args.length==4)
                    in = new Scanner( new File(args[3]) ); //Create Scanner for file
            }
            else{
                throw new Exception();
            }
	    	
    	}
    	//Exit on errors
    	catch (FileNotFoundException e){
            System.err.printf("Could not find file %s.\n",args[1]);
            System.exit(1);
    	}
    	catch (Exception e){ //Tell usage and exit
            System.err.println("Usage: java BufferManager <buffer size> <block size> <access type (random|LRU)> [query filename]");
            System.exit(1);
    	}
    	return in;
    }
    
    // returns select statement
    public static String[] querySelect(String theQuery){
    	
    	theQuery = theQuery.toLowerCase();
    	
    	// Get string from beginning up until From or ;
    	int i = theQuery.indexOf("from");
    	if (i == -1 || theQuery.indexOf(';') < i){
    		i = theQuery.indexOf(';');
    	}
    	theQuery = theQuery.substring(6,i);
    	
    	//System.out.println("theQuery = " + theQuery);
    	
    	String delims = "[ ,;]\\s*";
        String[] tokens = theQuery.split(delims);
        
    	return tokens;
    }
    
    // returns from
    public static String[] queryFrom(String theQuery){
    	theQuery = theQuery.toLowerCase();
    	
    	// Get string from 'from' up until where or ;
    	int i = theQuery.indexOf("where");
    	if (i == -1 || theQuery.indexOf(';') < i){
    		i = theQuery.indexOf(';');
    	}
    	theQuery = theQuery.substring(theQuery.indexOf("from")+4,i);
    	
    	//System.out.println("theQuery = " + theQuery);
    	
    	String delims = "[ ;]\\s*";
        String[] tokens = theQuery.split(delims);
        
    	return tokens;
    }
    
    // returns where
    public static String[] queryWhere(String theQuery){
    	theQuery = theQuery.toLowerCase();
    	
    	// Get string from 'from' up until where or ;
    	int i = theQuery.indexOf(';');
        if (theQuery.indexOf("where")!=-1)
            theQuery = theQuery.substring(theQuery.indexOf("where")+5,i);
        else
            return new String[0];
        
    	
    	//System.out.println("theQuery = " + theQuery);
    	
    	//String delims = "[ ;]\\s*|\\b";
        //String delims = "\\s+|;\\s*|(?<=[a-z])(?=[<>=])|(?<=[<>=])(?=(?:\"|\\d))|(?=&&)|(?=\\|\\|)"; //This got a lot more complicated, whoops
        String delims = "\\s+|;\\s*|(?<=[a-z])(?=[<>=])|(?<=[<>=])(?=(?:[^><=]))|(?=&&)|(?=\\|\\|)"; //OH GOD IT JUST KEEPS GETTING WORSE (actually this was just changing a quote or number to a non operator
        String[] tokens = theQuery.split(delims);
        
        for (int j=0;j<tokens.length;j++){
            if (tokens[j].matches("(?:[<>]=?|=)[^><=]")){
                tokens[j]=tokens[j].split("[<>]=?|=")[1];
            }
        }
        System.out.println();
        
    	return tokens;
    }
    
    /**
     * Opens a scanner from the indicated table, whether it's from a file or a join.
     * @param fromString queryFrom(query);
     * @return Scanner over the tuples in the correct table. That may be on a file or a string.
     * @throws FileNotFoundException 
     */
    //@SuppressWarnings("resource")
    public static Scanner openFile(String[] fromString){
    	Scanner r = null; //Return Scanner
    	int state = 0;
    	Scanner s = null;
    	for (String file: fromString){
            switch (file){
            case "": 
                //Empty string, do nothing.
                break;
            case "employee": //Open a scanner on the employee file
                try{
                	if(r == null){
                		r = new Scanner(new File("employee.txt")); //Open the scanner on employee.txt
                	}else{
                		String w = r.nextLine();
                		s = new Scanner(new File("employee.txt"));
                		w += ',' + s.nextLine() + '\n';
                		while (r.hasNext()){
                			s = new Scanner(new File("employee.txt"));
                			s.nextLine();
                			String rline = r.nextLine();
                			while(s.hasNext()){
                				String sline = s.nextLine();
                				w += join(rline,sline,state);
                			}
                			//r = new Scanner(w);
                		}
                		r = new Scanner(w);
                	}
                } catch (Exception e){
                    System.err.printf("Don't delete %s.\n","employee.txt"); //Someone deleted employee.txt
                }
                break;
            case "department": //Very similar to above.
                try {
                	if(r == null){
                		r = new Scanner(new File("department.txt")); //Open the scanner on employee.txt
                	}else{
                		String w = r.nextLine();
                		s = new Scanner(new File("department.txt"));
                		w += ',' + s.nextLine() + '\n';
                		while (r.hasNext()){
                			s = new Scanner(new File("department.txt"));
                			s.nextLine();
                			String rline = r.nextLine();
                			while(s.hasNext()){
                				String sline = s.nextLine();
                				w += join(rline,sline,state);
                			}
                			//r = new Scanner(w);
                		}
                		r = new Scanner(w);
                	}
                }  catch (Exception e){
                    System.err.printf("Don't delete %s.\n","department.txt");
                }
                break;
            case "employee_department": //Again, very similar
                try {
                	if(r == null){
                		r = new Scanner(new File("employee-department.txt")); //Open the scanner on employee.txt
                	}else{
                		String w = r.nextLine();
                		s = new Scanner(new File("employee-department.txt"));
                		w += ',' + s.nextLine() + '\n';
                		while (r.hasNext()){
                			s = new Scanner(new File("employee-department.txt"));
                			s.nextLine();
                			String rline = r.nextLine();
                			while(s.hasNext()){
                				String sline = s.nextLine();
                				w += join(rline,sline,state);
                			}
                			//r = new Scanner(w);
                		}
                		r = new Scanner(w);
                	}
                } catch (FileNotFoundException e) { //For some reason I decided to not use the generic exception. Weird.
                    System.err.printf("Don't delete %s.\n","employee-department.txt");
                }
                break;
            case "join":
            	state = 0;
            	break;
            default:
                System.err.printf("Syntax error in FROM statement\n");
                System.exit(2);
            }
    	}
    	
    	if (r==null){
            System.err.printf("Syntax error in FROM statement\n");
            System.exit(2);
    	}
    	
    	return r;
    }
    
    private static String join(String rline, String sline, int state) {
    	switch(state){
    	case 0:
    		// JOIN
    		rline += ',' + sline + '\n';
    		break;
    	default:
    		System.err.println("There's a problem in from statement.");
    		System.exit(2);
    	}
		return rline;
	}

    public static boolean passesCheck(TreeMap<String,String> tuple,String[] checkRules){
        boolean r=true;
        String lvalue=null;
        String op=null;
        String boolOp=null;
        
        
        
        for (String s: checkRules){
            if (s.equals("")){
                continue;
            }
            else if (s.matches("^(?:\\|\\||&&)$")){
                boolOp=s;
            }
            else if (s.matches("^(?:[><]?=|[><])$")){
                op=s;
            }
            else if (tuple.keySet().contains(s)){
                if (lvalue==null){
                    
                    lvalue=tuple.get(s);
                    
                }
                else{
                    s=tuple.get(s);
                    
                    r=doComparison(lvalue,op,s,boolOp,r);
                    
                    lvalue=null;
                    op=null;
                    boolOp=null;
                }
            }
            else {
                
                if (s.matches("\".*\"")){
                    s=s.substring(1,s.length()-1);
                }
                else if ( !s.matches("\\d+")){
                    return false;
                }
                r=doComparison(lvalue,op,s,boolOp,r);
                
                lvalue=null;
                op=null;
                boolOp=null;
            }
        }
        if (boolOp!=null)
            return false;
    	return r;
    }
    
    private static void printBoxHeader(String[] projectionList) {
        boolean printfinal=true;
        System.out.print('+');
        
        for (String s: projectionList){
            if (!s.equals("")){
                if (s.length()>14)
                    s=s.substring(0,14);
                int end=(15-s.length())>>1;
                for (int i=0;i<end;i++){
                    System.out.print('-');
                }
                System.out.print(s);
                
                end=end-1*((s.length()%2==1)?1:0);
                for (int i=0;i<end;i++){
                    System.out.print('-');
                }
                printfinal=false;
                System.out.print('+');
            }
        }
        if (printfinal)
            System.out.print('+');
        System.out.println();
    }

    private static void printBoxFooter(String[] projectionList) {
        boolean printfinal=true;
        System.out.print('+');
        
        for (String s: projectionList){
            if (!s.equals("")){
                for (int i=0;i<14;i++){
                    System.out.print('-');
                }
                System.out.print('+');
                printfinal=false;
            }
        }
        if (printfinal)
            System.out.print('+');
        System.out.println();
    }

    private static void printBoxLine(String[] projectionList, TreeMap<String,String> tuple) {
        boolean printfinal=true;
        System.out.print('|');
        
        for (String s:projectionList){
            if (s.equals(""))
                continue;
            String value = tuple.get(s);
            
            int end;
            end=(15-value.length())>>1;
            for (int i=0;i<end;i++){
                System.out.print(' ');
            }
            System.out.print(value);

            end=end-1*((value.length()%2==1)?1:0);
            for (int i=0;i<end;i++){
                System.out.print(' ');
            }
            System.out.print('|');
            printfinal=false;
        }
        
        if (printfinal)
            System.out.print('|');
        System.out.println();
    }

    private static void processQuery(String[] projectionList, Scanner objectIn, String[] sList) {
        boolean done=false; //Tracks if we've gone through the entire file
        String sTuple=objectIn.nextLine(); //First line in the file
        String[] columns = sTuple.split(","); //Split the first line to get the column names
        
        if (projectionList[1].equals("*")){
            projectionList=columns;
        }
        
        //If buffer is empty, copy stuff into the buffer
        if (buffer[0]==null){
            objectIn=copyIntoBuffer(objectIn,columns);
        }
        
        printBoxHeader(projectionList);
    	
        while (!done){
            if (!objectIn.hasNext()){ //Check if we've finished with the file
                done=true;
            }
            for (int i=0;i<bufferSize;i++){ //Check each line in the buffer and print the ones that pass the where condition
                if (buffer[i]!=null && notChecked[i]  //If it's not null (in case the number of records<buffer size) and it's not been checked (in case remaining records<buffer size)
                        && passesCheck(buffer[i],sList) ){ //And it passes the where condition
                    
                    printBoxLine(projectionList,buffer[i]); //Print the appropriate columns
                    timestamps[i]=Instant.now();
                }
                notChecked[i]=false;
            }
            
            objectIn=copyIntoBuffer(objectIn,columns); //Copy the next set of records into the buffer
    	}
        printBoxFooter(projectionList);
        System.out.println("Access Count: " + accessCount);
    }
    
    private static boolean leastRecentlyUsed(int i) {
        boolean used = true;
    	if (timestamps[i] == null){
            used = true;
    	}
        else{
            for(int j = 0; j < bufferSize; j++){
                if (j != i){
                    if (timestamps[j] != null && timestamps[i].compareTo(timestamps[j]) > 0){
                        used = false;
                    }
                }
            }
    	}
    	return used;
    }
    
    private static int leastRecentlyUsed(){
        int r=0;
        for (int i=0;i<bufferSize;i++){
            if (timestamps[i]==null){
                r=i;
                break;
            }
            if (timestamps[i].compareTo(timestamps[r]) < 0)
                r=i;
        }
        return r;
    }

    private static Scanner copyIntoBuffer(Scanner objectIn,String[] columns) {
        if (randomMode)
            return copyIntoBufferRandom(objectIn,columns);
        else
            return copyIntoBufferLRU(objectIn,columns);
    }
    
    /**
     * Copies from a table into the buffer
     * @param objectIn Scanner over the table object
     * @param columns The names of the columns in the table
     * @return 
     */
    private static Scanner copyIntoBufferRandom(Scanner objectIn,String[] columns) {
        accessCount++;
        for (int j=0;j<blockSize;j++){
            try{
                String sTuple = objectIn.nextLine();
                String[] attributes = sTuple.split(","); //Split the tuple into its attributes/columns
                TreeMap<String,String> tuple = new TreeMap(); //This will eventually go into the buffer
                for (int i=0;i<columns.length;i++){ //For each column name
                    //Put a key value pair of (column name, attribute) into the buffer.
                    tuple.put(columns[i],attributes[i]);
                }
                
                Random rand = new Random();
                int i;
                do{
                    i=rand.nextInt(bufferSize);
                } while (notChecked[i]);
                
                buffer[i]=tuple;
                notChecked[i]=true;
            }
            catch (NoSuchElementException e){ //If the file is empty, stop trying to read from the file
                break;
            }
        }
        
        //I don't think we need this return but it's not hurting anything, so whatever.
        return objectIn;
    }

    private static Scanner copyIntoBufferLRU(Scanner objectIn, String[] columns) {
        accessCount++;
        for (int j=0;j<bufferSize;j++){
            try{
                
                String sTuple = objectIn.nextLine();
                String[] attributes = sTuple.split(","); //Split the tuple into its attributes/columns
                TreeMap<String,String> tuple = new TreeMap(); //This will eventually go into the buffer
                for (int i=0;i<columns.length;i++){ //For each column name
                    //Put a key value pair of (column name, attribute) into the buffer.
                    tuple.put(columns[i],attributes[i]);
                }
                int i=leastRecentlyUsed();
                
                
                
                buffer[i]=tuple;
                notChecked[i]=true;
                timestamps[i]= Instant.now();
                //System.out.printf("%d %s %s %s\n",i,buffer[i],notChecked[i],timestamps[i]);
            }
            catch (NoSuchElementException e){ //If the file is empty, stop trying to read from the file
                break;
            }
        }
        
        //I don't think we need this return but it's not hurting anything, so whatever.
        return objectIn;
    }

    private static boolean doComparison(String lvalue, String op, String rvalue, String boolOp, boolean r) {
        int compvalue;
        
        
        
        //Figure out the comparison value
        if ( rvalue.matches("\\d+")){
            Integer i=Integer.parseInt(lvalue);
            Integer j=Integer.parseInt(rvalue);

            compvalue=i.compareTo(j);
        }
        else{
            compvalue = lvalue.compareTo(rvalue);
        }

        //Figure out if the comparison value is part of the operator
        boolean sr;
        if (compvalue<0 && op.indexOf('<')!=-1){
            sr=true;
        }
        else if (compvalue==0 && op.indexOf('=')!=-1){
            sr=true;
        }
        else if (compvalue>0 && op.indexOf('>')!=-1){
            sr=true;
        }
        else{
            sr=false;
        }



        //Apply any boolean operators
        if (boolOp==null){
            r=sr;
        }
        else if (boolOp.equals("&&")){
            r=r && sr;
            boolOp=null;
        }
        else if (boolOp.equals("||")){
            r=r || sr;
            boolOp=null;
        }
        
        return r;
    }
}
