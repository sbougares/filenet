/**
	IBM grants you a nonexclusive copyright license to use all programming code 
	examples from which you can generate similar function tailored to your own 
	specific needs.

	All sample code is provided by IBM for illustrative purposes only.
	These examples have not been thoroughly tested under all conditions.  IBM, 
	therefore cannot guarantee or imply reliability, serviceability, or function of 
	these programs.

	All Programs or code component contained herein are provided to you “AS IS “ 
	without any warranties of any kind.
	The implied warranties of non-infringement, merchantability and fitness for a 
	particular purpose are expressly disclaimed.

	© Copyright IBM Corporation 2007, ALL RIGHTS RESERVED.
 */

package com.filenet.apice.services.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.EngineCollection;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.constants.AutoClassify;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.CheckinType;
import com.filenet.api.constants.ComponentRelationshipType;
import com.filenet.api.constants.CompoundDocumentState;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.constants.VersionBindType;
import com.filenet.api.core.ComponentRelationship;
import com.filenet.api.core.Containable;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.CustomObject;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.ReferentialContainmentRelationship;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.security.AccessPermission;
import com.filenet.api.util.Id;

/**
 * Provides the static methods for making API
 * calls to Content Engine.
 */
public class CEUtil
{
    /*
     * Creates the file using bytes read from Document's
     * content stream.
     */
	public static void writeDocContentToFile(Document doc, String path)
    {
    	String fileName = doc.get_Name();
    	File f = new File(path,fileName);
    	InputStream is = doc.accessContentStream(0);
    	int c = 0;
    	try 
        {
        	FileOutputStream out = new FileOutputStream(f);
        	c = is.read();
        	while ( c != -1)
        	{
        		out.write(c);
        		c = is.read();
        	}
			is.close();
			out.close();
		} 
    	catch (IOException e) 
		{
			e.printStackTrace();
		}
    }
    
    /*
     * Reads the content from a file and stores it
     * in a byte array. The byte array will later be
     * used to create ContentTransfer object.
     */
	public static byte[] readDocContentFromFile(File f)
    {
        FileInputStream is;
        byte[] b = null;
        int fileLength = (int)f.length();
        if(fileLength != 0)
        {
        	try
        	{
        		is = new FileInputStream(f);
        		b = new byte[fileLength];
        		is.read(b);
        		is.close();
        	}
        	catch (FileNotFoundException e)
        	{
        		e.printStackTrace();
        	}
        	catch (IOException e)
        	{
        		e.printStackTrace();
        	}
        }
        return b;
    }
	
    /*
     * Creates the ContentTransfer object from supplied file's
     * content.
     */
	public static ContentTransfer createContentTransfer(File f)
    {
        ContentTransfer ctNew = null;
        if(readDocContentFromFile(f) != null)
        {
        	ctNew = Factory.ContentTransfer.createInstance();
            ByteArrayInputStream is = new ByteArrayInputStream(readDocContentFromFile(f));
            ctNew.setCaptureSource(is);
            ctNew.set_RetrievalName(f.getName());
        }
        return ctNew;
    }
    
    /*
     * Creates the ContentElementList from ContentTransfer object.
     */
	public static ContentElementList createContentElements(File f)
    {
        ContentElementList cel = null;
        if(createContentTransfer(f) != null)
        {
        	cel = Factory.ContentElement.createList();
            ContentTransfer ctNew = createContentTransfer(f);
            cel.add(ctNew);
        }
        return cel;
    }
    
    /*
     * Creates the Document with content from supplied file.
     */
	public static Document createDocWithContent(File f, String mimeType, ObjectStore os, String docName, String docClass)
    {
        Document doc = null;
		if (docClass.equals(""))
        	doc = Factory.Document.createInstance(os, null);
        else
        	doc = Factory.Document.createInstance(os, docClass);
        doc.getProperties().putValue("DocumentTitle", docName);
        doc.set_MimeType(mimeType);
        ContentElementList cel = CEUtil.createContentElements(f);
        if (cel != null)
        	doc.set_ContentElements(cel);
        return doc;
    }
    
    /*
     * Creates the Document without content.
     */
	public static Document createDocNoContent(String mimeType, ObjectStore os, String docName, String docClass)
    {
        Document doc = null;
		if (docClass.equals(""))
        	doc = Factory.Document.createInstance(os, null);
        else
        	doc = Factory.Document.createInstance(os, docClass);
        doc.getProperties().putValue("DocumentTitle", docName);
        doc.set_MimeType(mimeType);
        return doc;
    }
    
    /*
     * Retrives the Document object specified by path.
     */
	public static Document fetchDocByPath(ObjectStore os, String path)
    {
        Document doc = Factory.Document.fetchInstance(os, path, null);
        return doc;
    }
    
    /*
     * Retrives the Document object specified by id.
     */
	public static Document fetchDocById(ObjectStore os, String id)
    {
        Id id1 = new Id(id);
        Document doc = Factory.Document.fetchInstance(os, id1, null);
        return doc;
    }
    
    /*
     * Checks in the Document object.
     */
	public static void checkinDoc(Document doc)
    {
        doc.checkin(AutoClassify.AUTO_CLASSIFY, CheckinType.MINOR_VERSION);
        doc.save(RefreshMode.REFRESH);
        doc.refresh();
    }
    
    /*
     * Creates the CustomObject.
     */
	public static CustomObject createCustomObject(ObjectStore os, String className)
    {
        CustomObject co = null;
        if (className.equals(""))
        	co = Factory.CustomObject.createInstance(os,null);
        else
        	co = Factory.CustomObject.createInstance(os, className);
        return co;
    }
    
	/*
     * Retrives the CustomObject object specified by path.
     */
	public static CustomObject fetchCustomObjectByPath(ObjectStore os, String path)
    {
        CustomObject doc = Factory.CustomObject.fetchInstance(os,path,null);
        return doc;
    }
    
	/*
     * Retrives the CustomObject object specified by id.
     */
	public static CustomObject fetchCustomObjectById(ObjectStore os, String id)
    {
        Id id1 = new Id(id);
        CustomObject doc = Factory.CustomObject.fetchInstance(os,id1,null);
        return doc;
    }
    
    /*
     * Files the Containable object (i.e. Document, CustomObject) in
     * specified folder.
     */
	public static ReferentialContainmentRelationship fileObject(ObjectStore os, Containable o, String folderPath)
    {
    	Folder fo = Factory.Folder.fetchInstance(os,folderPath,null);
    	ReferentialContainmentRelationship rcr;
    	if (o instanceof Document)
    		rcr = fo.file((Document) o, AutoUniqueName.AUTO_UNIQUE,
                    ((Document) o).get_Name(), DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
    	else
    		rcr = fo.file((CustomObject) o, AutoUniqueName.AUTO_UNIQUE,
                    ((CustomObject) o).get_Name(), DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
    	return rcr;
    }
    
    /*
     * Retrives some of the properties of Containable object 
     * (i.e. Document, CustomObject) and stores them in a HashMap, 
     * with property name as key and property value as value.
     */
	public static HashMap getContainableObjectProperties(Containable o)
    {
        HashMap hm = new HashMap();
        hm.put("Id", o.get_Id().toString());
        hm.put("Name", o.get_Name());
        hm.put("Creator", o.get_Creator());
        hm.put("Owner", o.get_Owner());
        hm.put("Date Created",o.get_DateCreated().toString());
        hm.put("Date Last Modified", o.get_DateLastModified().toString());
        return hm;
    }
    
    /*
     * Creates the Folder object at specified path using specified name.
     */
	public static void createFolder(ObjectStore os, String fPath, String fName, String className)
    {
        Folder f = Factory.Folder.fetchInstance(os, fPath, null);
        Folder nf = null;
        if (className.equals(""))
        	nf = Factory.Folder.createInstance(os, null);
        else
        	nf = Factory.Folder.createInstance(os, className);
		nf.set_Parent(f);
		nf.set_FolderName(fName);
        nf.save(RefreshMode.REFRESH);
    }
    
    /*
     * Creates the CompoundDocument (i.e. ComponentRelationship object).
     */
	public static ComponentRelationship createComponentRelationship(ObjectStore os, String pTitle, String cTitle)
    {
		ComponentRelationship cr = null;
		Document parentDoc = null;
		Document childDoc = null;

		parentDoc = Factory.Document.createInstance(os, null);
		parentDoc.getProperties().putValue("DocumentTitle", pTitle);
		parentDoc.set_CompoundDocumentState(CompoundDocumentState.COMPOUND_DOCUMENT);
		parentDoc.save(RefreshMode.REFRESH);
		parentDoc.checkin(AutoClassify.AUTO_CLASSIFY, CheckinType.MINOR_VERSION);
		parentDoc.save(RefreshMode.REFRESH);

		childDoc = Factory.Document.createInstance(os, null);
		childDoc.getProperties().putValue("DocumentTitle", cTitle);
		childDoc.set_CompoundDocumentState(CompoundDocumentState.COMPOUND_DOCUMENT);
		childDoc.save(RefreshMode.REFRESH);
		childDoc.checkin(AutoClassify.AUTO_CLASSIFY, CheckinType.MINOR_VERSION);
		childDoc.save(RefreshMode.REFRESH);

		cr = Factory.ComponentRelationship.createInstance(os, null);
		cr.set_ParentComponent(parentDoc);
		cr.set_ChildComponent(childDoc);
		cr.set_ComponentRelationshipType(ComponentRelationshipType.DYNAMIC_CR);
		cr.set_VersionBindType(VersionBindType.LATEST_VERSION);

		return cr;
    }
    
    /*
     * Retrives the CompoundDocument object using supplied ID.
     */
	public static ComponentRelationship fetchComponentRelationship(ObjectStore os, String id)
    {
    	Id id1 = new Id(id);
    	ComponentRelationship cr = Factory.ComponentRelationship.fetchInstance(os, id1, null);
    	return cr;
    }
    
	/*
     * Retrives the some of the properties of CompoundDocument 
     * (i.e. ComponentRelationship object) and stores them in HashMap, 
     * property name as key and property value as value.
     */
	public static HashMap getComponentRelationshipObjectProperties(ComponentRelationship o)
    {
        HashMap hm = new HashMap();
        hm.put("Id", o.get_Id().toString());
        hm.put("Creator", o.get_Creator());
        hm.put("Date Created",o.get_DateCreated().toString());
        hm.put("Date Last Modified", o.get_DateLastModified().toString());
        hm.put("Child Component", o.get_ChildComponent().get_Name());
        hm.put("Parent Component", o.get_ParentComponent().get_Name());
        return hm;
    }
    
    /*
     * Retrives the RepositoryRowSet (result of querying Content Engine).
     * Query is constructed from supplied select, from, and where clause using
     * SearchSQL object. Then it creates the SearchScope object using supplied
     * ObjectStore, and queries the Content Engine using fetchRows method
     * of SearchScope object.
     */
    public static RepositoryRowSet fetchResultsRowSet(ObjectStore os, String select,
    		String from, String where, int rows)
    {
    	RepositoryRowSet rrs = null;
    	SearchSQL q = new SearchSQL();
    	SearchScope ss = new SearchScope(os);
    	q.setSelectList(select);
    	q.setFromClauseInitialValue(from, null, false);
    	if(!where.equals(""))
    	{
    		q.setWhereClause(where);
    	}
    	if(!(rows == 0))
    	{
    		q.setMaxRecords(rows);
    	}
    	rrs = ss.fetchRows(q, null, null, null);
    	return rrs;
    }
    
    /*
     * Gets column names to display in JTable. It takes
     * RepositoryRow as argument
     */
    public static Vector getResultProperties(RepositoryRow rr)
    {
    	Vector column = new Vector();
    	Properties ps = rr.getProperties();
    	Iterator it = ps.iterator();
    	
    	while(it.hasNext())
    	{
    		Property pt = (Property) it.next();
    		String name = pt.getPropertyName();
    		column.add(name);
    	}
    	return column;
    }
    
    /*
     * Retrives the properties from supplied RepositoryRow,
     * stores them in vector, and returns it.
     */
    public static Vector getResultRow(RepositoryRow rr)
    {
    	Vector row = new Vector();
    	Properties ps = rr.getProperties();
    	Iterator it = ps.iterator();
    	
    	while(it.hasNext())
    	{
    		Property pt = (Property) it.next();
    		Object value = pt.getObjectValue();
    		if (value == null)
    		{
    			row.add("null");
    		}
    		else if (value instanceof EngineCollection)
    		{
    			row.add("*");
    		}
    		else
    		{
    			row.add(value.toString());
    		}
    	}
    	return row;
    }
    
    /*
     * Retrives the access permission list for a Containable
     * object, stores it in Vector, and returns it.
     */
    public static Vector getPermissions(Containable co)
    {
        Vector permissions = new Vector();
        AccessPermissionList apl = co.get_Permissions();
        Iterator iter = apl.iterator();
        while (iter.hasNext())
        {
            AccessPermission ap = (AccessPermission)iter.next();
            permissions.add("GRANTEE_NAME: " + ap.get_GranteeName()); 
            permissions.add("ACCESS_MASK: " + ap.get_AccessMask().toString()); 
            permissions.add("ACCESS_TYPE: " + ap.get_AccessType().toString());
        }
        return permissions;
    }
}
