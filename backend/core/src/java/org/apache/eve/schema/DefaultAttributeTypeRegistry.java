/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.eve.schema;


import org.apache.ldap.common.schema.AttributeType;

import java.util.Map;
import java.util.HashMap;
import javax.naming.NamingException;


/**
 * A plain old java object implementation of an AttributeTypeRegistry.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultAttributeTypeRegistry implements AttributeTypeRegistry
{
    /** maps an OID to an AttributeType */
    private final Map byOid;
    /** maps an OID to a schema name*/
    private final Map oidToSchema;
    /** the registry used to resolve names to OIDs */
    private final OidRegistry oidRegistry;
    /** monitor notified via callback events */
    private AttributeTypeRegistryMonitor monitor;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------


    /**
     * Creates an empty DefaultAttributeTypeRegistry.
     */
    public DefaultAttributeTypeRegistry( OidRegistry oidRegistry )
    {
        this.byOid = new HashMap();
        this.oidToSchema = new HashMap();
        this.oidRegistry = oidRegistry;
        this.monitor = new AttributeTypeRegistryMonitorAdapter();
    }


    /**
     * Sets the monitor that is to be notified via callback events.
     *
     * @param monitor the new monitor to notify of notable events
     */
    public void setMonitor( AttributeTypeRegistryMonitor monitor )
    {
        this.monitor = monitor;
    }


    // ------------------------------------------------------------------------
    // Service Methods
    // ------------------------------------------------------------------------


    public void register( String schema, AttributeType attributeType ) throws NamingException
    {
        if ( byOid.containsKey( attributeType.getOid() ) )
        {
            NamingException e = new NamingException( "attributeType w/ OID " +
                attributeType.getOid() + " has already been registered!" );
            monitor.registerFailed( attributeType, e );
            throw e;
        }

        String[] names = attributeType.getAllNames();
        for ( int ii = 0; ii < names.length; ii++ )
        {
            oidRegistry.register( names[0], attributeType.getName() );
        }

        oidToSchema.put( attributeType.getOid(), schema );
        byOid.put( attributeType.getOid(), attributeType );
        monitor.registered( attributeType );
    }


    public AttributeType lookup( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );

        if ( ! byOid.containsKey( id ) )
        {
            NamingException e = new NamingException( "attributeType w/ OID "
                + id + " not registered!" );
            monitor.lookupFailed( id, e );
            throw e;
        }

        AttributeType attributeType = ( AttributeType ) byOid.get( id );
        monitor.lookedUp( attributeType );
        return attributeType;
    }


    public boolean hasAttributeType( String id )
    {
        if ( oidRegistry.hasOid( id ) )
        {
            try
            {
                return byOid.containsKey( oidRegistry.getOid( id ) );
            }
            catch ( NamingException e )
            {
                return false;
            }
        }

        return false;
    }


    public String getSchemaName( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );
        if ( oidToSchema.containsKey( id ) )
        {
            return ( String ) oidToSchema.get( id );
        }

        throw new NamingException( "OID " + id + " not found in oid to " +
            "schema name map!" );
    }
}
