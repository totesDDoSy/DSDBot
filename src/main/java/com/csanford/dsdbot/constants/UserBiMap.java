package com.csanford.dsdbot.constants;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author csanford
 * @date Sep 24, 2018
 */
public class UserBiMap
{

	private static final Map<String, String> DTOS_USERS;
	private static final Map<String, String> STOD_USERS;

	static
	{
		DTOS_USERS = new HashMap<>();
		DTOS_USERS.put( SecureConstants.RANDY_DID, SecureConstants.RANDY_SID );
		DTOS_USERS.put( SecureConstants.JACOB_DID, SecureConstants.JACOB_SID );
		DTOS_USERS.put( SecureConstants.MASON_DID, SecureConstants.MASON_SID );
		DTOS_USERS.put( SecureConstants.CORA_DID, SecureConstants.CORA_SID );
		DTOS_USERS.put( SecureConstants.WILL_DID, SecureConstants.WILL_SID );
		DTOS_USERS.put( SecureConstants.CODY_DID, SecureConstants.CODY_SID );
		DTOS_USERS.put( SecureConstants.DSD_DID, "dsd-bot" );

		STOD_USERS = new HashMap<>();
		STOD_USERS.put( SecureConstants.RANDY_SID, SecureConstants.RANDY_DID );
		STOD_USERS.put( SecureConstants.JACOB_SID, SecureConstants.JACOB_DID );
		STOD_USERS.put( SecureConstants.MASON_SID, SecureConstants.MASON_DID );
		STOD_USERS.put( SecureConstants.CORA_SID, SecureConstants.CORA_DID );
		STOD_USERS.put( SecureConstants.WILL_SID, SecureConstants.WILL_DID );
		STOD_USERS.put( SecureConstants.CODY_SID, SecureConstants.CODY_DID );
	}

	public static String get( String key )
	{
		return DTOS_USERS.getOrDefault( key, STOD_USERS.get( key ) );
	}

	public static String getOrDefault( String key, String def )
	{
		return get( key ) == null ? def : get( key );
	}
}
