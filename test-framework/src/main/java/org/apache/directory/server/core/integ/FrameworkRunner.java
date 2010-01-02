/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.integ;


import java.lang.reflect.Field;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.changelog.ChangeLog;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The class responsible for running all the tests. t read the annotations, 
 * initialize the DirectoryService, call each test and do the cleanup at the end.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class FrameworkRunner extends BlockJUnit4ClassRunner
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( FrameworkRunner.class );

    /** The 'service' field in the run tests */
    private static final String DIRECTORY_SERVICE_FIELD_NAME = "service";
    
    /** The 'ldapServer' field in the run tests */
    private static final String LDAP_SERVER_FIELD_NAME = "ldapServer";
    
    /** The filed used to tell the test that it is run in a suite */
    private static final String IS_RUN_IN_SUITE_FIELD_NAME = "isRunInSuite";

    /** The suite this class depend on, if any */
    private FrameworkSuite suite;

    /** The DirectoryService for this class, if any */
    private DirectoryService classDS;

    /** The LdapServer for this class, if any */
    private LdapServer classLdapServer;

    /**
     * Creates a new instance of FrameworkRunner.
     */
    public FrameworkRunner( Class<?> clazz ) throws InitializationError
    {
        super( clazz );
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void run( final RunNotifier notifier )
    {
        // Before running any test, check to see if we must create a class DS
        // Get the LdapServerBuilder, if any
        CreateLdapServer classLdapServerBuilder = getDescription().getAnnotation( CreateLdapServer.class );

        try
        {
            classDS = DSAnnotationProcessor.getDirectoryService( getDescription() );
            long revision = 0L;
            DirectoryService directoryService = null;
            
            if ( classDS != null )
            {
                // We have a class DS defined, update it
                directoryService = classDS;
                
                // Get the applyLdifs for each level and apply them
                if ( suite != null )
                {
                    DSAnnotationProcessor.applyLdifs( suite.getDescription(), classDS );
                }
                
                DSAnnotationProcessor.applyLdifs( getDescription(), classDS );
            }
            else
            {
                // No class DS. Do we have a Suite ?
                if ( suite != null ) 
                {
                    // yes. Do we have a suite DS ?
                    directoryService = suite.getDirectoryService();

                    if ( directoryService != null )
                    {
                        // yes : apply the class LDIFs only, and tag for reversion
                        revision = getCurrentRevision( directoryService );

                        // apply the class LDIFs
                        DSAnnotationProcessor.applyLdifs( getDescription(), directoryService );
                    }
                    else
                    {
                        // No : define a default DS for the suite then
                        DirectoryServiceFactory dsf = DefaultDirectoryServiceFactory.DEFAULT;
                        
                        directoryService = dsf.getDirectoryService();
                        // enable CL explicitly cause we are not using DSAnnotationProcessor
                        directoryService.getChangeLog().setEnabled( true );

                        dsf.init( "default" + UUID.randomUUID().toString() );
                        
                        // Stores it into the suite
                        suite.setDirectoryService( directoryService );
                        
                        // Apply the suite LDIF first
                        DSAnnotationProcessor.applyLdifs( suite.getDescription(), directoryService );
                        
                        // Then tag for reversion and apply the class LDIFs
                        revision = getCurrentRevision( directoryService );
                        
                        DSAnnotationProcessor.applyLdifs( getDescription(), directoryService );
                    }
                }
                else
                {
                    // No : define a default class DS then
                    DirectoryServiceFactory dsf = DefaultDirectoryServiceFactory.DEFAULT;
                    
                    directoryService = dsf.getDirectoryService();
                    // enable CL explicitly cause we are not using DSAnnotationProcessor
                    directoryService.getChangeLog().setEnabled( true );

                    dsf.init( "default" + UUID.randomUUID().toString() );
                    
                    // Stores the defaultDS in the classDS
                    classDS = directoryService;

                    // Apply the class LDIFs
                    DSAnnotationProcessor.applyLdifs( getDescription(), directoryService );
                }
            }

            // check if it has a LdapServerBuilder
            // then use the DS created above
            if( classLdapServerBuilder != null )
            {
                int minPort = 0;
                
                if ( suite != null )
                {
                    LdapServer suiteServer = suite.getLdapServer();
                    
                    for ( Transport transport : suiteServer.getTransports() )
                    {
                        if ( minPort <= transport.getPort() )
                        {
                            minPort = transport.getPort();
                        }
                    }
                }
                
                classLdapServer = ServerAnnotationProcessor.getLdapServer( getDescription(), directoryService, minPort + 1 );
            }
            else if( suite != null && suite.getLdapServer() != null )
            {
                classLdapServer = suite.getLdapServer();
                directoryService = classLdapServer.getDirectoryService();
                // no need to inject the LDIF data that would have been done above
                // if ApplyLdifs is present
            }

            // Now run the class
            super.run( notifier );

            if( classLdapServer != null )
            {
                if( suite == null || suite.getLdapServer() != classLdapServer )
                {
                    classLdapServer.stop();
                }
            }
            
            // cleanup classService if it is not the same as suite service or
            // it is not null (this second case happens in the absence of a suite)
            if ( classDS != null )
            {
                LOG.debug( "Shuting down DS for {}", classDS.getInstanceId() );
                classDS.shutdown();
                FileUtils.deleteDirectory( classDS.getWorkingDirectory() );
            }
            else
            {
                // Revert the ldifs
                // We use a class or suite DS, just revert the current test's modifications
                revert( directoryService, revision );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Failed to run the class {}", getTestClass().getName() );
            LOG.error( e.getMessage() );
            e.printStackTrace();
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void runChild( FrameworkMethod method, RunNotifier notifier )
    {
        // Don't run the test if the @Ignored annotation is used
        if ( method.getAnnotation( Ignore.class ) != null )
        {
            Description description = describeChild( method );
            notifier.fireTestIgnored( description );
            return;
        }

        // Get the applyLdifs for each level
        Description suiteDescription = null;
        
        if ( suite != null )
        {
            suiteDescription = suite.getDescription();
        }
        
        Description classDescription = getDescription();
        Description methodDescription = describeChild( method );

        // Ok, ready to run the test
        try
        {
            DirectoryService directoryService = null;
            
            // Set the revision to 0, we will revert only if it's set to another value
            long revision = 0L;

            // Check if this method has a dedicated DSBuilder
            DirectoryService methodDS = DSAnnotationProcessor.getDirectoryService( methodDescription );

            // we don't support method level LdapServer so
            // we check for the presence of Class level LdapServer first 
            if( classLdapServer != null )
            {
                directoryService = classLdapServer.getDirectoryService();
                
                revision = getCurrentRevision( directoryService );
                
                DSAnnotationProcessor.applyLdifs( methodDescription, directoryService );
            }
            else if ( methodDS != null )
            {
                // Apply all the LDIFs
                DSAnnotationProcessor.applyLdifs( suiteDescription, methodDS );
                DSAnnotationProcessor.applyLdifs( classDescription, methodDS );
                DSAnnotationProcessor.applyLdifs( methodDescription, methodDS );
                
                directoryService = methodDS;
            }
            else if ( classDS != null )
            {
                directoryService = classDS;
                
                // apply the method LDIFs, and tag for reversion
                revision = getCurrentRevision( directoryService );
                
                DSAnnotationProcessor.applyLdifs( methodDescription, directoryService );
            }
            else if ( suite != null )
            {
                directoryService = suite.getDirectoryService();
                
                // apply the method LDIFs, and tag for reversion
                revision = getCurrentRevision( directoryService );
                
                DSAnnotationProcessor.applyLdifs( methodDescription, directoryService );
            }

            
            // At this point, we know which service to use.
            // Inject it into the class
            Field field = getTestClass().getJavaClass().getField( DIRECTORY_SERVICE_FIELD_NAME );
            field.set( getTestClass().getJavaClass(), directoryService );
            
            // if we run this class in a suite, tell it to the test
            field = getTestClass().getJavaClass().getField( IS_RUN_IN_SUITE_FIELD_NAME );
            field.set( getTestClass().getJavaClass(), suite != null );
            
            if( classLdapServer != null )
            {
                field = getTestClass().getJavaClass().getField( DIRECTORY_SERVICE_FIELD_NAME );
                field.set( getTestClass().getJavaClass(), classLdapServer.getDirectoryService() );
                
                field = getTestClass().getJavaClass().getField( LDAP_SERVER_FIELD_NAME );
                field.set( getTestClass().getJavaClass(), classLdapServer );
            }
            

            // Run the test
            super.runChild( method, notifier );

            // Cleanup the methodDS if it has been created
            if ( methodDS != null )
            {
                LOG.debug( "Shuting down DS for {}", methodDS.getInstanceId() );
                methodDS.shutdown();
                FileUtils.deleteDirectory( methodDS.getWorkingDirectory() );
            }
            else
            {
                // We use a class or suite DS, just revert the current test's modifications
                revert( directoryService, revision );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Failed to run the method {}", method );
            LOG.error( "", e );
            e.printStackTrace();
        }
    }


    /**
     * Set the Suite reference into this class
     *
     * @param suite The suite this classd is contained into
     */
    public void setSuite( FrameworkSuite suite )
    {
        this.suite = suite;
    }


    /**
     * @return The Suite this class is contained nto, if any
     */
    public FrameworkSuite getSuite()
    {
        return suite;
    }
    
    
    private long getCurrentRevision( DirectoryService dirService ) throws Exception
    {
        if( ( dirService != null ) && ( dirService.getChangeLog().isEnabled() ) )
        {
            long revision = dirService.getChangeLog().getCurrentRevision(); 
            LOG.debug( "Create revision {}", revision );

            return revision;
        }
        
        return 0;
    }
    
    
    private void revert( DirectoryService dirService, long revision ) throws Exception
    {
        if ( dirService == null )
        {
            return;
        }

        ChangeLog cl = dirService.getChangeLog();
        if ( cl.isEnabled() && ( revision < cl.getCurrentRevision() ) )
        {
            LOG.debug( "Revert revision {}", revision );
            dirService.revert( revision );
        }
    }
}
