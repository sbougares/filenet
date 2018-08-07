package com.tn.filenet.subs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.CustomObject;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.events.UpdateEvent;
import com.filenet.api.property.Properties;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;
import com.filenet.rm.bds.BDSConstants;
import com.filenet.rm.bds.BatchResultItem;
import com.filenet.rm.bds.BulkDeclarationService;
import com.filenet.rm.bds.ContainerReference;
import com.filenet.rm.bds.DataType;
import com.filenet.rm.bds.DocumentReference;
import com.filenet.rm.bds.PropertyValue;
import com.filenet.rm.bds.RecordDefinition;
import com.filenet.rm.bds.exception.BDSException;
import com.filenet.rm.bds.impl.BulkDeclarationFactory;

public class BoxHandler implements EventActionHandler{

	private static final String VERSION_SERIES = "VersionSeries";
	private static final String RECORD_INFORMATION = "RecordInformation";
	private static final String HOME_LOCATION = "HomeLocation";
	private static final String LOCATION = "Location";
	private static final String DOCUMENT_TITLE = "DocumentTitle";
	private static final String CONTRACT_NUMBER = "ContractNumber";
	private static final String BOX_HANDLER_DECLARE = "BoxHandlerDeclare";
	private static final String DOCUMENT = "Document";
	private static final String SOURCE_OBJECT_STORE = "COPS";
	private static final String ID = "Id";
	private static final String CONTRACT_NUMBER_END = "ContractNumberEnd";
	private static final String CONTRACT_NUMBER_START = "ContractNumberStart";

	private static class CNInterval {
		String start;
		String end;
		boolean startInclusive;
		boolean endInclusive;
		
		public CNInterval(String start, String end, boolean startInclusive,
				boolean endInclusive) {
			super();
			this.start = start;
			this.end = end;
			this.startInclusive = startInclusive;
			this.endInclusive = endInclusive;
		}
		
		@Override
		public String toString() {
			String ret=startInclusive?"(":")";
			ret+=start;
			ret+=";";
			ret+=end;
			ret+=endInclusive?")":"(";
			
			return ret;
		}
	}
	
	@Override
	public void onEvent(ObjectChangeEvent arg0, Id arg1)
	{
		Folder f=(Folder) arg0.get_SourceObject();
		f.refresh(new String[]{CONTRACT_NUMBER_START,CONTRACT_NUMBER_END,ID,HOME_LOCATION,LOCATION});
		
		String cnStart=f.getProperties().getStringValue(CONTRACT_NUMBER_START);
		String cnEnd=f.getProperties().getStringValue(CONTRACT_NUMBER_END);
		
		String origStart=null;
		String origEnd=null;
		
		if(arg0 instanceof UpdateEvent) {
			UpdateEvent ue = (UpdateEvent)arg0;
			Properties origProps = ue.get_OriginalObject().getProperties();
			
			if(ue.get_ModifiedProperties().contains(CONTRACT_NUMBER_START)) {
				origStart=origProps.getStringValue(CONTRACT_NUMBER_START);
				if(origStart.equals(cnStart)) origStart=null;
			}
			
			if(ue.get_ModifiedProperties().contains(CONTRACT_NUMBER_END)) {
				origEnd=origProps.getStringValue(CONTRACT_NUMBER_END);
				if(origEnd.equals(cnEnd)) origEnd=null;
			}
			
			if(origStart==null && origEnd==null) return;
		}
						
		List<CNInterval> added=new ArrayList<CNInterval>();
		List<CNInterval> removed=new ArrayList<CNInterval>();
		
		if(origStart==null&&origEnd==null) {
			added.add(new CNInterval(cnStart,cnEnd,true,true));
		} else if((origEnd!=null && cnStart.compareTo(origEnd)>0)||(origStart!=null && origStart.compareTo(cnEnd)>0)) {
			removed.add(new CNInterval(origStart, origEnd, true, true));
			added.add(new CNInterval(cnStart, cnEnd, true, true));
		} else {
			if(origStart!=null) {
				if(cnStart.compareTo(origStart)>0) {
					removed.add(new CNInterval(origStart, cnStart, true, false));
				} else {
					added.add(new CNInterval(cnStart, origStart, true, false));
				}
			}
			if(origEnd!=null) {
				if(cnEnd.compareTo(origEnd)>0) {
					added.add(new CNInterval(origEnd, cnEnd, false, true));
				} else {
					removed.add(new CNInterval(cnEnd, origEnd, false, true));
				}
			}
		}
		
		ObjectStore sourceOs = Factory.ObjectStore.getInstance(Factory.Domain.getInstance(Factory.Connection.getConnection(arg0.getConnection().getURI()),null),SOURCE_OBJECT_STORE);
		sourceOs.refresh(new String[]{ID});

		ObjectStore fpos = arg0.getObjectStore();
		fpos.refresh(new String[]{ID});
		
		for(CNInterval cni:removed) {
			undeclareAll(cni, sourceOs, fpos);
		}
		
		for(CNInterval cni:added) {
			declareAll(cni, fpos.get_Id().toString(),sourceOs,f);
		}

	}

	private void undeclareAll(CNInterval cni,ObjectStore sourceOs,ObjectStore fpos) {
		String query="SELECT "+ID+","+CONTRACT_NUMBER+" FROM CopsDocument WHERE ContractNumber IS NOT NULL AND IsCurrentVersion=true AND "+RECORD_INFORMATION+" IS NOT NULL";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.startInclusive?">=":">";
		query+="'"+cni.start+"'";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.endInclusive?"<=":"<";
		query+="'"+cni.end+"'";

		@SuppressWarnings("unchecked")
		Iterator<Document> docs=new SearchScope(sourceOs).fetchObjects(new SearchSQL(query), 1000, null, true).iterator();
		while(docs.hasNext()) {
			Document doc = docs.next();
			doc.getProperties().putObjectValue(RECORD_INFORMATION,null);
			doc.save(RefreshMode.NO_REFRESH);
		}

		query="SELECT "+VERSION_SERIES+" FROM COPSRecord WHERE ContractNumber IS NOT NULL AND IsCurrentVersion=true";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.startInclusive?">=":">";
		query+="'"+cni.start+"'";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.endInclusive?"<=":"<";
		query+="'"+cni.end+"'";

		ArrayList<Id> recordVs = new ArrayList<Id>();

		@SuppressWarnings("unchecked")
		Iterator<RepositoryRow> records=new SearchScope(fpos).fetchRows(new SearchSQL(query), 1000, null, true).iterator();
		while(records.hasNext()) {
			recordVs.add(((VersionSeries)records.next().getProperties().getObjectValue(VERSION_SERIES)).get_Id());
		}
		
		for(Id id:recordVs) {
			VersionSeries versionSeries = Factory.VersionSeries.getInstance(fpos,id);
			versionSeries.delete();
			versionSeries.save(RefreshMode.NO_REFRESH);
		}
	}

	private void declareAll(CNInterval cni,String fposId,ObjectStore sourceOs,Folder recordFolder) {
		String query="SELECT "+ID+","+CONTRACT_NUMBER+","+DOCUMENT_TITLE+" FROM CopsDocument WHERE ContractNumber IS NOT NULL AND IsCurrentVersion=true AND RecordInformation IS NULL";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.startInclusive?">=":">";
		query+="'"+cni.start+"'";
		query+=" AND "+CONTRACT_NUMBER;
		query+=cni.endInclusive?"<=":"<";
		query+="'"+cni.end+"'";

		List<DocumentReference> docRefs=new ArrayList<DocumentReference>();
		List<RecordDefinition> recordDefs=new ArrayList<RecordDefinition>();

		@SuppressWarnings("unchecked")
		Iterator<RepositoryRow> rows=new SearchScope(sourceOs).fetchRows(new SearchSQL(query), 1000, null, true).iterator();
		while(rows.hasNext()) {
			Properties rowprops = rows.next().getProperties();

			DocumentReference docRef = BulkDeclarationFactory.newDocumentReference(sourceOs.get_Id().toString(), DOCUMENT, rowprops.getIdValue(ID).toString());
	        
	        docRefs.add(docRef);
	        
	        RecordDefinition recordDef = BulkDeclarationFactory.newRecordDefinition(fposId,"COPSRecord");
	        recordDefs.add(recordDef);
	        
	        @SuppressWarnings("unchecked")
			List<PropertyValue> propertyValues = recordDef.getPropertyValues();

	        PropertyValue cn = BulkDeclarationFactory.newPropertyValue(CONTRACT_NUMBER, DataType.TYPE_STRING,false );
        	cn.setValue(rowprops.getStringValue(CONTRACT_NUMBER));
        	propertyValues.add(cn);
        	
        	PropertyValue dt = BulkDeclarationFactory.newPropertyValue(DOCUMENT_TITLE, DataType.TYPE_STRING,false );        	
        	dt.setValue(rowprops.getStringValue(DOCUMENT_TITLE));	        
        	propertyValues.add(dt);

        	PropertyValue hl = BulkDeclarationFactory.newPropertyValue(HOME_LOCATION, DataType.TYPE_OBJECT,false );
        	hl.setValue(BulkDeclarationFactory.newObjectReference(fposId, "CustomObject",((CustomObject)recordFolder.getProperties().getObjectValue(HOME_LOCATION)).get_Id().toString()));	        
        	propertyValues.add(hl);

        	PropertyValue l = BulkDeclarationFactory.newPropertyValue(LOCATION, DataType.TYPE_OBJECT,false );
        	l.setValue(BulkDeclarationFactory.newObjectReference(fposId, "CustomObject",((CustomObject)recordFolder.getProperties().getObjectValue(LOCATION)).get_Id().toString()));	        
        	propertyValues.add(l);

            @SuppressWarnings("unchecked")
			List<ContainerReference> containers = recordDef.getContainers();
            
            ContainerReference containerRef = BulkDeclarationFactory.newContainerReference(fposId, "Folder", recordFolder.get_Id().toString());
            containers.add(containerRef);        	
		}

		BulkDeclarationService bds = getBDSService(sourceOs.getConnection().getURI());

        bds.startBatch(BOX_HANDLER_DECLARE);
		
        for(int i=0;i<docRefs.size();i++) {
        	List<DocumentReference> dr=new ArrayList<DocumentReference>();
        	dr.add(docRefs.get(i));

        	bds.declareRecord(docRefs.get(i).getObjectIdent(), recordDefs.get(i), dr);
        }
        
        try {
        	bds.executeBatch();
        } catch(BDSException e) {
        	for(BatchResultItem bri:bds.getBatchResultItems()) {
        		if(bri.getResultValue() instanceof RuntimeException) throw (RuntimeException)bri.getResultValue();
        	}
        	
        	throw e;
        }
	}
	
	private BulkDeclarationService getBDSService(String ceURI) {
        Map<String,String> contextMap=new HashMap<String, String>();
        contextMap.put( BDSConstants.CONTEXT_SERVICE,  ceURI);
        contextMap.put( BDSConstants.CONTEXT_TRANSPORT_TYPE, BDSConstants.TRANSPORT_TYPE_BDP40_JACE);
        
       return BulkDeclarationFactory.getBulkDeclarationService(contextMap);            
	}
}
