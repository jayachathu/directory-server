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
package org.apache.ldap.server.interceptor;


import junit.framework.TestCase;
import org.apache.ldap.server.DirectoryServiceConfiguration;
import org.apache.ldap.server.DirectoryService;
import org.apache.ldap.server.DirectoryServiceListener;
import org.apache.ldap.server.partition.DirectoryPartitionNexusProxy;
import org.apache.ldap.server.jndi.DeadContext;
import org.apache.ldap.server.invocation.InvocationStack;
import org.apache.ldap.server.invocation.Invocation;
import org.apache.ldap.server.configuration.InterceptorConfiguration;
import org.apache.ldap.server.configuration.DirectoryPartitionConfiguration;
import org.apache.ldap.server.configuration.MutableInterceptorConfiguration;
import org.apache.ldap.common.filter.ExprNode;
import org.apache.ldap.common.name.LdapDN;

import javax.naming.NamingException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.Context;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import java.util.*;


/**
 * Unit test cases for InterceptorChain methods.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class InterceptorChainTest extends TestCase
{
    private final MockInterceptor[] interceptorArray =
    {
            new MockInterceptor( "0" ),
            new MockInterceptor( "1" ),
            new MockInterceptor( "2" ),
            new MockInterceptor( "3" ),
            new MockInterceptor( "4" )
    };
//    private final static Logger log = LoggerFactory.getLogger( InterceptorChainTest.class );
    private InterceptorChain chain;
    private List interceptors = new ArrayList( interceptorArray.length );


    protected void setUp() throws Exception
    {
        chain = new InterceptorChain();

        for ( int ii = 0; ii < interceptorArray.length; ii++ )
        {
            MutableInterceptorConfiguration config = new MutableInterceptorConfiguration();
            config.setInterceptor( interceptorArray[ii] );
            config.setName( interceptorArray[ii].getName() );
            chain.addLast( config );
        }
    }


    protected void tearDown() throws Exception
    {
        chain = null;
        interceptors.clear();
    }


    public void testNoBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn } );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( interceptorArray.length, interceptors.size() );
        for ( int ii = 0; ii < interceptorArray.length; ii++ )
        {
            assertEquals( interceptorArray[ii], interceptors.get( ii ) );
        }
    }


    public void testSingleBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn }, Collections.singleton( "0" ) );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( interceptorArray.length - 1, interceptors.size() );
        for ( int ii = 0; ii < interceptorArray.length; ii++ )
        {
            if ( ii != 0 )
            {
                assertEquals( interceptorArray[ii], interceptors.get( ii - 1 ) );
            }
        }
        assertFalse( interceptors.contains( interceptorArray[0] ) );
    }


    public void testAdjacentDoubleBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Collection bypass = new HashSet();
        bypass.add( "0" );
        bypass.add( "1" );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn }, bypass );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( interceptorArray.length - 2, interceptors.size() );
        for ( int ii = 0; ii < interceptorArray.length; ii++ )
        {
            if ( ii != 0 && ii != 1 )
            {
                assertEquals( interceptorArray[ii], interceptors.get( ii - 2 ) );
            }
        }
        assertFalse( interceptors.contains( interceptorArray[0] ) );
        assertFalse( interceptors.contains( interceptorArray[1] ) );
    }


    public void testFrontAndBackDoubleBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Collection bypass = new HashSet();
        bypass.add( "0" );
        bypass.add( "4" );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn }, bypass );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( interceptorArray.length - 2, interceptors.size() );
        assertEquals( interceptorArray[1], interceptors.get( 0 ) );
        assertEquals( interceptorArray[2], interceptors.get( 1 ) );
        assertEquals( interceptorArray[3], interceptors.get( 2 ) );
        assertFalse( interceptors.contains( interceptorArray[0] ) );
        assertFalse( interceptors.contains( interceptorArray[4] ) );
    }


    public void testDoubleBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Collection bypass = new HashSet();
        bypass.add( "1" );
        bypass.add( "3" );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn }, bypass );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( interceptorArray.length - 2, interceptors.size() );
        assertEquals( interceptorArray[0], interceptors.get( 0 ) );
        assertEquals( interceptorArray[2], interceptors.get( 1 ) );
        assertEquals( interceptorArray[4], interceptors.get( 2 ) );
        assertFalse( interceptors.contains( interceptorArray[1] ) );
        assertFalse( interceptors.contains( interceptorArray[3] ) );
    }


    public void testCompleteBypass() throws NamingException
    {
        Name dn = new LdapDN( "ou=system" );
        Context ctx = new DeadContext();
        DirectoryService ds = new MockDirectoryService();
        DirectoryPartitionNexusProxy proxy = new DirectoryPartitionNexusProxy( ctx, ds );
        Invocation i = new Invocation( proxy, ctx, "lookup", new Object[]{ dn },
                DirectoryPartitionNexusProxy.BYPASS_ALL_COLLECTION );
        InvocationStack.getInstance().push( i );

        try
        {
            chain.lookup( dn );
        }
        catch( Exception e )
        {
        }

        assertEquals( 0, interceptors.size() );
    }


    class MockInterceptor implements Interceptor
    {
        String name;

        public MockInterceptor( String name )
        {
            this.name = name;
        }


        public String getName()
        {
            return this.name;
        }


        public void init( DirectoryServiceConfiguration factoryCfg, InterceptorConfiguration cfg ) throws NamingException
        {
        }


        public void destroy()
        {
        }


        public Attributes getRootDSE( NextInterceptor next ) throws NamingException
        {
            interceptors.add( this );
            return next.getRootDSE();
        }


        public Name getMatchedName( NextInterceptor next, Name name, boolean normalized ) throws NamingException
        {
            interceptors.add( this );
            return next.getMatchedName( name, normalized );
        }


        public Name getSuffix( NextInterceptor next, Name name, boolean normalized ) throws NamingException
        {
            interceptors.add( this );
            return next.getSuffix( name, normalized );
        }


        public Iterator listSuffixes( NextInterceptor next, boolean normalized ) throws NamingException
        {
            interceptors.add( this );
            return next.listSuffixes( normalized );
        }


        public void addContextPartition( NextInterceptor next, DirectoryPartitionConfiguration cfg ) throws NamingException
        {
            interceptors.add( this );
            next.addContextPartition( cfg );
        }


        public void removeContextPartition( NextInterceptor next, Name suffix ) throws NamingException
        {
            interceptors.add( this );
            next.removeContextPartition( suffix );
        }


        public boolean compare( NextInterceptor next, Name name, String oid, Object value ) throws NamingException
        {
            interceptors.add( this );
            return next.compare( name, oid, value );
        }


        public void delete( NextInterceptor next, Name name ) throws NamingException
        {
            interceptors.add( this );
            next.delete( name );
        }


        public void add( NextInterceptor next, String userProvidedName, Name normalizedName, Attributes entry ) throws NamingException
        {
            interceptors.add( this );
            next.add( userProvidedName, normalizedName, entry );
        }


        public void modify( NextInterceptor next, Name name, int modOp, Attributes attributes ) throws NamingException
        {
            interceptors.add( this );
            next.modify( name, modOp, attributes );
        }


        public void modify( NextInterceptor next, Name name, ModificationItem [] items ) throws NamingException
        {
            interceptors.add( this );
            next.modify( name, items );
        }


        public NamingEnumeration list( NextInterceptor next, Name baseName ) throws NamingException
        {
            interceptors.add( this );
            return next.list( baseName );
        }


        public NamingEnumeration search( NextInterceptor next, Name baseName, Map environment, ExprNode filter, SearchControls searchControls ) throws NamingException
        {
            interceptors.add( this );
            return next.search( baseName, environment, filter, searchControls );
        }


        public Attributes lookup( NextInterceptor next, Name name ) throws NamingException
        {
            interceptors.add( this );
            return next.lookup( name );
        }


        public Attributes lookup( NextInterceptor next, Name dn, String [] attrIds ) throws NamingException
        {
            interceptors.add( this );
            return next.lookup( dn, attrIds );
        }


        public boolean hasEntry( NextInterceptor next, Name name ) throws NamingException
        {
            interceptors.add( this );
            return next.hasEntry( name );
        }


        public boolean isSuffix( NextInterceptor next, Name name ) throws NamingException
        {
            interceptors.add( this );
            return next.isSuffix( name );
        }


        public void modifyRn( NextInterceptor next, Name name, String newRn, boolean deleteOldRn ) throws NamingException
        {
            interceptors.add( this );
            next.modifyRn( name, newRn, deleteOldRn );
        }


        public void move( NextInterceptor next, Name oldName, Name newParentName ) throws NamingException
        {
            interceptors.add( this );
            next.move( oldName, newParentName );
        }


        public void move( NextInterceptor next, Name oldName, Name newParentName, String newRn, boolean deleteOldRn ) throws NamingException
        {
            interceptors.add( this );
            next.move( oldName, newParentName, newRn, deleteOldRn );
        }
    }


    class MockDirectoryService extends DirectoryService
    {
        public void startup( DirectoryServiceListener listener, Hashtable environment ) throws NamingException
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }


        public void shutdown() throws NamingException
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }


        public void sync() throws NamingException
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }


        public boolean isStarted()
        {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }


        public DirectoryServiceConfiguration getConfiguration()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }


        public Context getJndiContext( String baseName ) throws NamingException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }


        public Context getJndiContext( String principal, byte[] credential, String authentication, String baseName ) throws NamingException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
