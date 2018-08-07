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
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.VersionSeries;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.events.UpdateEvent;
import com.filenet.api.exception.EngineRuntimeException;
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

public class ContractDeclarer implements EventActionHandler {

	private static final String CONTRACT_DECLARE = "ContractDeclare";
	private static final String CUSTOM_OBJECT = "CustomObject";
	private static final String HOME_LOCATION = "HomeLocation";
	private static final String LOCATION = "Location";
	private static final String COPS_RECORD = "COPSRecord";
	private static final String DOCUMENT = "Document";
	private static final String CONTRACT_NUMBER_END = "ContractNumberEnd";
	private static final String CONTRACT_NUMBER_START = "ContractNumberStart";
	private static final String BOX = "Box";
	private static final String FPOS = "FPOS";
	private static final String DOCUMENT_TITLE = "DocumentTitle";
	private static final String VERSION_SERIES = "VersionSeries";
	private static final String ID = "Id";
	private static final String RECORD_INFORMATION = "RecordInformation";
	private static final String CONTRACT_NUMBER = "ContractNumber";

	@Override
	public void onEvent(ObjectChangeEvent objectchangeevent, Id id)
			throws EngineRuntimeException {

		if((objectchangeevent instanceof UpdateEvent)&&!((UpdateEvent)objectchangeevent).get_ModifiedProperties().contains(CONTRACT_NUMBER)) return;

		Document doc=(Document) objectchangeevent.get_SourceObject();
		doc.refresh(new String[]{DOCUMENT_TITLE,CONTRACT_NUMBER,RECORD_INFORMATION,ID});
		
		String cn = doc.getProperties().getStringValue(CONTRACT_NUMBER);
		if(cn!=null && (cn=cn.trim()).length()==0) cn=null;

		Document record = (Document) doc.getProperties().getObjectValue(RECORD_INFORMATION);
		if(record!=null) {
			record.refresh(new String[]{VERSION_SERIES});
			VersionSeries vs = record.get_VersionSeries();
			
			doc.getProperties().putObjectValue(RECORD_INFORMATION, null);
			doc.save(RefreshMode.REFRESH);
			
			vs.delete();
			vs.save(RefreshMode.NO_REFRESH);
		}
		
		if(cn!=null) {
			ObjectStore fpos = Factory.ObjectStore.getInstance(Factory.Domain.getInstance(objectchangeevent.getConnection(), null),FPOS);
			fpos.refresh(new String[]{ID});
			String fposId = fpos.get_Id().toString();
						
			@SuppressWarnings("unchecked")
			Iterator<RepositoryRow> folders = new SearchScope(fpos).fetchRows(new SearchSQL("SELECT "+ID+","+LOCATION+","+HOME_LOCATION+" FROM "+BOX+" WHERE "+CONTRACT_NUMBER_START+"<='"+cn+"'"+" AND "+CONTRACT_NUMBER_END+">='"+cn+"'"), 1, null, false).iterator();
			if(folders.hasNext()) {
				RepositoryRow recordFolder = folders.next();
				
		        Map<String,String> contextMap=new HashMap<String, String>();
		        contextMap.put( BDSConstants.CONTEXT_SERVICE,  objectchangeevent.getConnection().getURI());
		        contextMap.put( BDSConstants.CONTEXT_TRANSPORT_TYPE, BDSConstants.TRANSPORT_TYPE_BDP40_JACE);
		        
		        BulkDeclarationService bds = BulkDeclarationFactory.getBulkDeclarationService(contextMap);		       				

		        bds.startBatch(CONTRACT_DECLARE);
		        
		        ObjectStore os = objectchangeevent.getObjectStore();
		        os.refresh(new String[]{ID});

		        List<DocumentReference> docRefs=new ArrayList<DocumentReference>();
		        docRefs.add(BulkDeclarationFactory.newDocumentReference(os.get_Id().toString(), DOCUMENT, doc.get_Id().toString()));
		        
		        RecordDefinition recordDef = BulkDeclarationFactory.newRecordDefinition(fposId,COPS_RECORD);
		        
		        @SuppressWarnings("unchecked")
				List<PropertyValue> propertyValues = recordDef.getPropertyValues();

		        PropertyValue cnp = BulkDeclarationFactory.newPropertyValue(CONTRACT_NUMBER, DataType.TYPE_STRING,false );
	        	cnp.setValue(cn);
	        	propertyValues.add(cnp);
	        	
	        	PropertyValue dt = BulkDeclarationFactory.newPropertyValue(DOCUMENT_TITLE, DataType.TYPE_STRING,false );        	
	        	dt.setValue(doc.getProperties().getStringValue(DOCUMENT_TITLE));	        
	        	propertyValues.add(dt);

	        	PropertyValue hl = BulkDeclarationFactory.newPropertyValue(HOME_LOCATION, DataType.TYPE_OBJECT,false );
	        	hl.setValue(BulkDeclarationFactory.newObjectReference(fposId, CUSTOM_OBJECT,((CustomObject)recordFolder.getProperties().getObjectValue(HOME_LOCATION)).get_Id().toString()));	        
	        	propertyValues.add(hl);

	        	PropertyValue l = BulkDeclarationFactory.newPropertyValue(LOCATION, DataType.TYPE_OBJECT,false );
	        	l.setValue(BulkDeclarationFactory.newObjectReference(fposId, CUSTOM_OBJECT,((CustomObject)recordFolder.getProperties().getObjectValue(LOCATION)).get_Id().toString()));	        
	        	propertyValues.add(l);

	            @SuppressWarnings("unchecked")
				List<ContainerReference> containers = recordDef.getContainers();
	            
	            ContainerReference containerRef = BulkDeclarationFactory.newContainerReference(fposId, "Folder", recordFolder.getProperties().getIdValue(ID).toString());
	            containers.add(containerRef);        	
		       
				bds.declareRecord(doc.get_Id().toString(),recordDef,docRefs);
				
				try {
					bds.executeBatch();
				} catch(BDSException e) {
					for(BatchResultItem bri:bds.getBatchResultItems()) if(bri.getResultValue() instanceof RuntimeException) throw (RuntimeException)bri.getResultValue();
					
					throw e;
				}
			}			
		}		
	}
}
