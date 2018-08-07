package com.tn.filenet.subs;

import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.events.UpdateEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.property.Properties;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;

public class BoxUniquenessEnforcer implements EventActionHandler{
	private static final String UNIQUENESS_QUERY = "SELECT TOP 1 Id FROM %s WHERE This<>OBJECT('%s') AND %s<='%s' AND %s>='%s'";
	private static final String ID = "Id";
	private static final String BOX = "Box";
	private static final String CONTRACT_NUMBER_END = "ContractNumberEnd";
	private static final String CONTRACT_NUMBER_START = "ContractNumberStart";

	@Override
	public void onEvent(ObjectChangeEvent arg0, Id arg1)
	{
		Folder f=(Folder) arg0.get_SourceObject();
		f.refresh(new String[]{CONTRACT_NUMBER_START,CONTRACT_NUMBER_END,ID});
		
		String cnStart=f.getProperties().getStringValue(CONTRACT_NUMBER_START);
		String cnEnd=f.getProperties().getStringValue(CONTRACT_NUMBER_END);
		String id=f.getProperties().getIdValue(ID).toString();
		
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
		
		if(cnStart.compareTo(cnEnd)>0)
			throw new EngineRuntimeException(new Exception("ContractStart must be less than or equal to ContractEnd!"),ExceptionCode.E_BAD_VALUE,null);

		ObjectStore fpos = arg0.getObjectStore();

		String query=String.format(UNIQUENESS_QUERY,BOX,id,CONTRACT_NUMBER_START,cnEnd,CONTRACT_NUMBER_END,cnStart);	
		if(!new SearchScope(fpos).fetchRows(new SearchSQL(query), 1, null, false).isEmpty())
			throw new EngineRuntimeException(new Exception("Overlapping ContractNumber intervals!"),ExceptionCode.E_NOT_UNIQUE,null);
		
	}

}
