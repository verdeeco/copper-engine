/*
 * Copyright 2002-2012 SCOOP Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.scoopgmbh.copper.persistent;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.scoopgmbh.copper.Response;
import de.scoopgmbh.copper.Workflow;
import de.scoopgmbh.copper.batcher.BatchCommand;
import de.scoopgmbh.copper.batcher.Batcher;
import de.scoopgmbh.copper.persistent.txn.TransactionController;
import de.scoopgmbh.copper.persistent.txn.Transactional;

/**
 * Oracle implementation of the {@link ScottyDBStorageInterface}.
 * It supports multiple engines (cluster) connected to one database. 
 * 
 * @author austermann
 *
 */
public class ScottyDBStorage implements ScottyDBStorageInterface {

	private static final Logger logger = LoggerFactory.getLogger(ScottyDBStorage.class);

	private DatabaseDialect dialect;
	private TransactionController transactionController;

	private Batcher batcher;
	private long deleteStaleResponsesIntervalMsec = 60L*60L*1000L;
	private int defaultStaleResponseRemovalTimeout = 60*60*1000;

	private Thread enqueueThread;
	private ScheduledExecutorService scheduledExecutorService;
	private boolean shutdown = false;
	private final Map<String, ResponseLoader> responseLoaders = new HashMap<String, ResponseLoader>();

	public ScottyDBStorage() {

	}
	
	public void setTransactionController(TransactionController transactionController) {
		this.transactionController = transactionController;
	}
	
	public void setDialect(DatabaseDialect dialect) {
		this.dialect = dialect;
	}
	
	protected  <T> T run(final Transactional<T> txn) throws Exception {
		return transactionController.run(txn);
	}
	
	/**
	 * Sets the default removal timeout for stale responses in the underlying database. A response is stale/timed out when
	 * there is no workflow instance waiting for it within the specified amount of time. 
	 * @param defaultStaleResponseRemovalTimeout
	 */
	public void setDefaultStaleResponseRemovalTimeout(int defaultStaleResponseRemovalTimeout) {
		this.defaultStaleResponseRemovalTimeout = defaultStaleResponseRemovalTimeout;
	}
	
	public void setBatcher(Batcher batcher) {
		this.batcher = batcher;
	}
	
	public void setDeleteStaleResponsesIntervalMsec(long deleteStaleResponsesIntervalMsec) {
		this.deleteStaleResponsesIntervalMsec = deleteStaleResponsesIntervalMsec;
	}

	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#resumeBrokenBusinessProcesses()
	 */
	private void resumeBrokenBusinessProcesses() throws Exception {
		logger.info("resumeBrokenBusinessProcesses");
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				dialect.resumeBrokenBusinessProcesses(con);
				return null;
			}
		});
	}

	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#insert(de.scoopgmbh.copper.Workflow)
	 */
	public void insert(final Workflow<?> wf) throws Exception {
		logger.trace("insert({})",wf);
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				dialect.insert(wf, con);
				return null;
			}
		});
	}

	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#insert(java.util.List)
	 */
	public void insert(final List<Workflow<?>> wfs) throws Exception {
		logger.trace("insert(wfs.size={})",wfs.size());
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				dialect.insert(wfs, con);
				return null;
			}
		});
	}

	@Override
	public List<Workflow<?>> dequeue(final String ppoolId, final int max) throws Exception {
		return run(new Transactional<List<Workflow<?>>>() {
			@Override
			public List<Workflow<?>> run(Connection con) throws Exception {
				return dialect.dequeue(ppoolId, max, con);
			}
		});
	}

	protected List<List<String>> splitt(Collection<String> keySet, int n) {
		if (keySet.isEmpty()) 
			return Collections.emptyList();

		List<List<String>> r = new ArrayList<List<String>>(keySet.size()/n+1);
		List<String> l = new ArrayList<String>(n);
		for (String s : keySet) {
			l.add(s);
			if (l.size() == n) {
				r.add(l);
				l = new ArrayList<String>(n);
			}
		}
		if (l.size() > 0) {
			r.add(l);
		}
		return r;
	}

	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#notify(java.util.List)
	 */
	public void notify(final List<Response<?>> response) throws Exception {
		for (Response<?> r : response)
			notify(r,null);
	}


	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#startup()
	 */
	public synchronized void startup() {
		try {
			deleteStaleResponse();
			resumeBrokenBusinessProcesses();
			
			enqueueThread = new Thread("ENQUEUE") {
				@Override
				public void run() {
					updateQueueState();
				}
			};
			enqueueThread.start();

			scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			
			scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					try {
						deleteStaleResponse();
					} 
					catch (Exception e) {
						logger.error("deleteStaleResponse failed",e);
					}
				}
			}, deleteStaleResponsesIntervalMsec, deleteStaleResponsesIntervalMsec, TimeUnit.MILLISECONDS);
		}
		catch(Exception e) {
			throw new Error("Unable to startup",e);
		}
	}

	private void deleteStaleResponse() throws Exception {
		if (logger.isTraceEnabled()) logger.trace("deleteStaleResponse()");

		int n = 0;
		final int MAX_ROWS = 20000;
		do {
			run(new Transactional<Integer>() {
				@Override
				public Integer run(Connection con) throws Exception {
					return dialect.deleteStaleResponse(con, MAX_ROWS);
				}
			});
		}
		while(n == MAX_ROWS);
	}

	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#shutdown()
	 */
	public synchronized void shutdown() {
		if (shutdown)
			return;
		
		shutdown = true;
		enqueueThread.interrupt();
		scheduledExecutorService.shutdown();
		synchronized (responseLoaders) {
			for (ResponseLoader responseLoader : responseLoaders.values()) {
				responseLoader.shutdown();
			}
		}
	}

	private void updateQueueState() {
		final int max = 5000;
		logger.info("started");
		while(!shutdown) {
			int x=0;
			try {
				x = run(new Transactional<Integer>() {
					@Override
					public Integer run(Connection con) throws Exception {
						return dialect.updateQueueState(max,con);
					}
				});
			}
			catch(Exception e) {
				logger.error("updateQueueState failed",e);
			}
			if (x == 0) {
				try {
					Thread.sleep(2000);
				} 
				catch (InterruptedException e) {
					//ignore
				}
			}
			else if (x < max) {
				try {
					Thread.sleep(1000);
				} 
				catch (InterruptedException e) {
					//ignore
				}
			}
		}
		logger.info("finished");
	}
	
	@Override
	public void insert(Workflow<?> wf, Connection con) throws Exception {
		if (con == null)
			insert(wf);
		else
			dialect.insert(wf, con);
	}

	@Override
	public void insert(List<Workflow<?>> wfs, Connection con) throws Exception {
		if (con == null)
			insert(wfs);
		else
			dialect.insert(wfs, con);
	}
	
	public void restart(final String workflowInstanceId) throws Exception {
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				dialect.restart(workflowInstanceId, con);
				return null;
			}
		});
	}

	@Override
	public void restartAll() throws Exception {
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				dialect.restartAll(con);
				return null;
			}
		});		
	}
	
	public int getDefaultStaleResponseRemovalTimeout() {
		return defaultStaleResponseRemovalTimeout;
	}

	@Override
	public void setRemoveWhenFinished(boolean removeWhenFinished) {
		dialect.setRemoveWhenFinished(removeWhenFinished);
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"}) 
	private void runSingleBatchCommand(final BatchCommand cmd) throws Exception {
		run(new Transactional<Void>() {
			@Override
			public Void run(Connection con) throws Exception {
				cmd.executor().doExec(Collections.singletonList(cmd), con);
				return null;
			}
		});
	}
	
	@Override
	public void notify(List<Response<?>> responses, Connection c) throws Exception {
		dialect.notify(responses, c);
	}		

	@Override
	public void error(Workflow<?> w, Throwable t) {
		if (logger.isTraceEnabled()) logger.trace("error("+w.getId()+","+t.toString()+")");
		try {
			executeBatchCommand(dialect.createBatchCommand4error(w, t));
		} 
		catch (Exception e) {
			logger.error("error failed",e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void executeBatchCommand(BatchCommand cmd) throws Exception {
		if (batcher != null) {
			batcher.submitBatchCommand(cmd);
		}
		else {
			runSingleBatchCommand(cmd);
		}
	}	
	
	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#registerCallback(de.scoopgmbh.copper.persistent.RegisterCall)
	 */
	public void registerCallback(final RegisterCall rc) throws Exception {
		if (logger.isTraceEnabled()) logger.trace("registerCallback("+rc+")");
		if (rc == null) 
			throw new NullPointerException();
		executeBatchCommand(dialect.createBatchCommand4registerCallback(rc, this));
	}	
	
	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#notify(de.scoopgmbh.copper.Response, java.lang.Object)
	 */
	public void notify(final Response<?> response, final Object callback) throws Exception {
		if (logger.isTraceEnabled()) logger.trace("notify("+response+")");
		if (response == null)
			throw new NullPointerException();
		executeBatchCommand(dialect.createBatchCommand4Notify(response));
	}	
	
	/* (non-Javadoc)
	 * @see de.scoopgmbh.copper.persistent.ScottyDBStorageInterface#finish(de.scoopgmbh.copper.Workflow)
	 */
	public void finish(final Workflow<?> w) {
		if (logger.isTraceEnabled()) logger.trace("finish("+w.getId()+")");
		try {
			executeBatchCommand(dialect.createBatchCommand4Finish(w));
		} 
		catch (Exception e) {
			logger.error("finish failed",e);
			error(w,e);
		}
	}	
}