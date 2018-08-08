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

import com.filenet.api.collection.FolderSet;
import com.filenet.api.collection.ReferentialContainmentRelationshipSet;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.ReferentialContainmentRelationship;

/**
 * This class provides static methods to make API calls
 * to Content Engine.
 */
public class FolderUtil
{
	private static ObjectStore os;
	
	/*
	 * API call to Content Engine to fetch Folder instance from
	 * specified ObjectStore and folder path.
	 */
	public static Folder fetchFolder(ObjectStore os, String folderPath)
	{
		Folder f = Factory.Folder.fetchInstance(os, folderPath, null);
		return f;
	}
	
	/*
	 * API call to Content Engine to retrieve subfolders of specified 
	 * Folder instance.
	 */
	public static FolderSet getSubFolders(Folder f)
	{
		FolderSet fs = f.get_SubFolders();
		return fs;
	}
	
	/*
	 * API call to Content Engine to retrieve FolderName property of
	 * specified Folder instance.
	 */
	public static String getFolderName(Folder f)
	{
		String name = f.get_FolderName();
		return name;
	}
	
	/*
	 * API call to Content Engine to retrieve containees of specified Folder instance. 
	 * It returns ReferentialContainmentRelationshipSet. You can iterate the
	 * ReferentialContainmentRelationshipSet  to get ReferentialContainmentRelationship objects,
	 * from which Documents and CustomObjects can be retrieved. 
	 */
	public static ReferentialContainmentRelationshipSet getFolderContainees(Folder f)
	{
		ReferentialContainmentRelationshipSet rcrSet = f.get_Containees();
		return rcrSet;
	}
	
	/*
	 * API call to Content Engine to retrieve ContainmentName property of 
	 * ReferentialContainmentRelationship object.
	 */
	public static String getContainmentName(ReferentialContainmentRelationship rcr)
	{
		String name = rcr.get_ContainmentName();
		return name;
	}

	/*
	 * Sets the ObjectStore based on user selection, from which
	 * folders will be retrieved.
	 */
	public static void setOs(ObjectStore os)
	{
		FolderUtil.os = os;
	}
	
	/*
	 * Returns the current ObjectStore.
	 */
	public static ObjectStore getOs()
	{
		return os;
	}
}
