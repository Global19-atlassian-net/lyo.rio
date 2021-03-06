/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 *     Samuel Padgett       - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.oslc.v3.sample;

import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.tdb.TDB;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb.base.block.FileMode;
import org.apache.jena.tdb.sys.SystemTDB;
import org.eclipse.lyo.oslc.v3.sample.vocab.LDP;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Stores resources using Jena TDB. Each resource has its own graph in the
 * dataset where the graph name is the resource URI. Defaults to an in-memory
 * dataset unless system property <code>dataset.dir</code> is set.
 */
public class Persistence {
	private static final Logger logger = LogManager.getLogger(Persistence.class);
	private final static Persistence instance = new Persistence();

	public static final String DATASET_DIR_PROP = "dataset.dir";
	private final static String DATASET_DIR = System.getProperty(DATASET_DIR_PROP);

	private final Dataset dataset;

	private Persistence() {
		TDB.setOptimizerWarningFlag(false);
		SystemTDB.setFileMode(FileMode.direct) ;
		logger.info("Using dataset directory: " + DATASET_DIR);
		if (DATASET_DIR == null) {
			dataset = TDBFactory.createDataset();
		} else {
			dataset = TDBFactory.createDataset(DATASET_DIR);
		}
	}

	public static Persistence getInstance() {
		return instance;
	}

	/**
	 * Begins a read transaction.
	 *
	 * @see <a href="https://jena.apache.org/documentation/tdb/tdb_transactions.html">TDB Transactions</a>
	 */
	public void readLock() {
		dataset.begin(ReadWrite.READ);
	}

	/**
	 * Begins a write transaction.
	 *
	 * @see <a href="https://jena.apache.org/documentation/tdb/tdb_transactions.html">TDB Transactions</a>
	 */
	public void writeLock() {
		dataset.begin(ReadWrite.WRITE);
	}

	/**
	 * Commits the changes.
	 *
	 * @see <a href="https://jena.apache.org/documentation/tdb/tdb_transactions.html">TDB Transactions</a>
	 */
	public void commit() {
		dataset.commit();
	}

	/**
	 * Ends the transaction.
	 *
	 * @see <a href="https://jena.apache.org/documentation/tdb/tdb_transactions.html">TDB Transactions</a>
	 */
	public void end() {
		dataset.end();
	}

	/**
	 * Reserves the next sequential ID. Must be called in the context of a write lock.
	 *
	 * @return the next ID
	 */
	public Long reserveID() {
		final Model defaultModel = dataset.getDefaultModel();
		final Resource resource = defaultModel.createResource("");
		final Property lastID = defaultModel.createProperty("http://www.eclipse.org/lyo/ns/oslc3#lastID");
		final long next;
		if (defaultModel.contains(resource, lastID)) {
			final Statement s = defaultModel.getProperty(resource, lastID);
			next = s.getLong() + 1;
			s.changeLiteralObject(next);
		} else {
			next = 1L;
			defaultModel.addLiteral(resource, lastID, next);
		}

		return next;
	}

	public void addContainmentTriples(Resource container) {
		final Model model = container.getModel();
		final Iterator<Node> iter = dataset.asDatasetGraph().listGraphNodes();
		while (iter.hasNext()) {
			Node n = iter.next();
			container.addProperty(LDP.contains, model.createResource(n.getURI()));
		}
	}

	public void addBugModel(Model m, URI location) {
		dataset.addNamedModel(location.toString(), m);
	}

	public Model getBugModel(String uri) {
		if (!dataset.containsNamedModel(uri)) {
			return null;
		}

		return dataset.getNamedModel(uri);
	}

	public void removeBugModel(String uri) {
		dataset.removeNamedModel(uri);
	}

	public boolean exists(String uri) {
		return dataset.containsNamedModel(uri);
	}

	public void query(String queryString, OutputStream out) {
		Query query = QueryFactory.create(queryString);
		QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
		qexec.getContext().set(TDB.symUnionDefaultGraph, true);
		ResultSet result = qexec.execSelect();
		try {
			ResultSetFormatter.outputAsJSON(out, result);
		} finally {
			qexec.close();
		}
	}
}
