/*
Raghu Tirumala
Scheduler.java

This scheduler implements a multi level feedback queue.
*/
import java.util.*;

public class Scheduler extends Thread
{
   private Vector queue0;
   private Vector queue1;
   private Vector queue2;
   
   private int timeSlice;
   private static final int DEFAULT_TIME_SLICE = 1000;

   // New data added to p161 
   private boolean[] tids; // Indicate which ids have been used
   private static final int DEFAULT_MAX_THREADS = 10000;

   // A new feature added to p161 
   // Allocate an ID array, each element indicating if that id has been used
   private int nextId = 0;
   private void initTid( int maxThreads ) {
      tids = new boolean[maxThreads];
      for ( int i = 0; i < maxThreads; i++ )
         tids[i] = false;
   }

   // A new feature added to p161 
   // Search an available thread ID and provide a new thread with this ID
   private int getNewTid( ) {
      for ( int i = 0; i < tids.length; i++ ) {
         int tentative = ( nextId + i ) % tids.length;
         if ( tids[tentative] == false ) {
            tids[tentative] = true;
            nextId = ( tentative + 1 ) % tids.length;
            return tentative;
         }
      }
      return -1;
   }

   // A new feature added to p161 
   // Return the thread ID and set the corresponding tids element to be unused
   private boolean returnTid( int tid ) {
      if ( tid >= 0 && tid < tids.length && tids[tid] == true ) {
         tids[tid] = false;
         return true;
      }
      return false;
   }

   // Retrieve the current thread's TCB from the queues
   // Modified to search queue1 and queue2
   public TCB getMyTcb( ) {
      Thread myThread = Thread.currentThread( ); // Get my thread object
      synchronized( queue0 ) {
         for ( int i = 0; i < queue0.size( ); i++ ) {
            TCB tcb = ( TCB )queue0.elementAt( i );
            Thread thread = tcb.getThread( );
            if ( thread == myThread ) // if this is my TCB, return it
               return tcb;
         }
      }
      synchronized( queue1 ) {
         for ( int i = 0; i < queue1.size( ); i++ ) {
            TCB tcb = ( TCB )queue1.elementAt( i );
            Thread thread = tcb.getThread( );
            if ( thread == myThread ) // if this is my TCB, return it
               return tcb;
         }
      }
      synchronized( queue2 ) {
         for ( int i = 0; i < queue2.size( ); i++ ) {
            TCB tcb = ( TCB )queue2.elementAt( i );
            Thread thread = tcb.getThread( );
            if ( thread == myThread ) // if this is my TCB, return it
               return tcb;
         }
      }
      return null;
   }

   // A new feature added to p161 
   // Return the maximal number of threads to be spawned in the system
   public int getMaxThreads( ) {
      return tids.length;
   }

   public Scheduler( ) {
      timeSlice = DEFAULT_TIME_SLICE;
      queue0 = new Vector( );
      queue1 = new Vector( );
      queue2 = new Vector( );
      initTid( DEFAULT_MAX_THREADS );
   }

   public Scheduler( int quantum ) {
      timeSlice = quantum;
      queue0 = new Vector( );
      queue1 = new Vector( );
      queue2 = new Vector( );
      initTid( DEFAULT_MAX_THREADS );
   }

   // A new feature added to p161 
   // A constructor to receive the max number of threads to be spawned
   public Scheduler( int quantum, int maxThreads ) {
      timeSlice = quantum;
      queue0 = new Vector( );
      queue1 = new Vector( );
      queue2 = new Vector( );
      initTid( maxThreads );
   }

   private void schedulerSleep( ) {
      try {
         Thread.sleep( timeSlice );
      } catch ( InterruptedException e ) {
      }
   }

   // A modified addThread of p161 example
   public TCB addThread( Thread t ) {
      //t.setPriority( 2 );
      TCB parentTcb = getMyTcb( ); // get my TCB and find my TID
      int pid = ( parentTcb != null ) ? parentTcb.getTid( ) : -1;
      int tid = getNewTid( ); // get a new TID
      if ( tid == -1)
         return null;
      TCB tcb = new TCB( t, tid, pid ); // create a new TCB
      queue0.add( tcb ); //add to queue0
      return tcb;
   }

   // A new feature added to p161
   // Removing the TCB of a terminating thread
   public boolean deleteThread( ) {
      TCB tcb = getMyTcb( ); 
      if ( tcb!= null )
         return tcb.setTerminated( );
      else
         return false;
   }

   public void sleepThread( int milliseconds ) {
      try {
         sleep( milliseconds );
      } catch ( InterruptedException e ) { }
   }

   private boolean runQueue0(Thread threadToRun){
      TCB runTCB = (TCB) queue0.elementAt(0); 
      if(threadCompleted(runTCB, queue0)) {
         return true;
      } else {
         threadToRun = runTCB.getThread();
         startOrResume(threadToRun);
         sleepThread(timeSlice/2);  //put the scheduler to sleep and let thread run
         pushToNextQueue(queue0, queue1, runTCB, threadToRun);
      }
      return false;
   }
  
   private boolean runQueue1(Thread threadToRun) {
      TCB runTCB = (TCB) queue1.elementAt(0); 
      if(threadCompleted(runTCB, queue0)) {
         return true;
      } else {
         threadToRun = runTCB.getThread();
         startOrResume(threadToRun);
         sleepThread(timeSlice/2);  //put the scheduler to sleep and let thread run
         if(queue0.size() > 0) { //Check for new input
            handleNewTasks(threadToRun, 1);
         }
         sleepThread(timeSlice/2);
         pushToNextQueue(queue1, queue2, runTCB, threadToRun);
      }
      return false;         
   }
  
   private boolean runQueue2(Thread threadToRun) {
      TCB runTCB = (TCB) queue2.elementAt(0); 
      if(threadCompleted(runTCB, queue1)) {
         return true;
      } else {
         threadToRun = runTCB.getThread();
         startOrResume(threadToRun);
         sleepThread(timeSlice/2);  //put the scheduler to sleep and let thread run
         if(queue0.size() > 0 || queue1.size() > 0) {
            handleNewTasks(threadToRun, 2);
         }
         sleepThread(timeSlice);
         sleepThread(timeSlice/2);
         pushToNextQueue(queue2, queue2, runTCB, threadToRun);
      }
      return false;      
   }
   
   //Pushes any incomplete threads in current queue to the next queue 
   private void pushToNextQueue(Vector currentQueue, Vector nextQueue, TCB currTCB, Thread threadToPush){
     synchronized (currentQueue) {
        if(threadToPush != null && threadToPush.isAlive( )) {
          threadToPush.suspend();                      
          currentQueue.remove(currTCB);           
          nextQueue.add(currTCB);              
        }
     }
   }
   
   //Checks if thread with TCB provided has completed execution.
   //Removes tcb from queue and return true if execution completed.
   //Returns false otherwise.
   private boolean threadCompleted(TCB tcbToCheck, Vector queue) {
      if(tcbToCheck.getTerminated()){
         queue.remove(tcbToCheck); 
         returnTid(tcbToCheck.getTid()); //update thread id array
         return true;  
      }
      return false;
   }
   
   //Starts or resumes thread passed in as parameter   
   private void startOrResume(Thread threadToRun) {
      if(threadToRun != null) {
         if(threadToRun.isAlive()){
            threadToRun.resume();
         } else {
            threadToRun.start();
         }
      }
   }
   
   private void handleNewTasks(Thread threadToPause, int queueNum) {
     if(threadToPause != null && threadToPause.isAlive()) {
        threadToPause.suspend();
        Thread newTask = null;
        runQueue0(newTask);
        if(queueNum == 2) {
           Thread newTask2 = null;
           runQueue1(newTask2);        
        }
        threadToPause.resume();
     }
   }
   
   // A modified run of p161
   public void run( ) {
      Thread current = null;

      while ( true ) {
         try {
            if(queue0.size() == 0 && queue1.size() == 0 && queue2.size() == 0) {
              continue;
            }
            if(queue0.size() > 0){
               runQueue0(current);
               continue;  
            }
            if(queue0.size() == 0 && queue1.size() > 0) {
               runQueue1(current);
               continue;
            }
            if(queue0.size() == 0 && queue1.size() == 0 && queue2.size() > 0) {
               runQueue2(current);
               continue;
            }
            
         } catch ( NullPointerException e3 ) { };
      } // while
   } // run
}
