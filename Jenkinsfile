@Library('libpipelines') _

hose {
    EMAIL = 'qa'
    DEVTIMEOUT = 30
    RELEASETIMEOUT = 30
    BUILDTOOL = 'maven'
    BUILDTOOL_IMAGE = "stratio/qa-builder:0.1.0-SNAPSHOT"

    ITSERVICES = [
        ['ZOOKEEPER': [
           'image': 'zookeeper:3.5.7',
           'env': [],
           'sleep': 30,
           'healthcheck': 2181]],
        ['MONGODB': [
           'image': 'stratio/mongo:3.0.4',
	       'healthcheck': 27017]],
        ['ELASTIC': [
                'image': 'amazon/opendistro-for-elasticsearch:1.13.2',
                'sleep': 600,
                'healthcheck': 9200,
                'resources': ['limits': ['memory': "2Gi"]],
                'env': ['discovery.type=single-node', 'opendistro_security.disabled=true']
            ]
            ],
        ['CASSANDRA': [
           'image': 'stratio/cassandra-lucene-index:3.0.7.3',
           'volumes':[
                 'jts:1.14.0'],
           'env': [
                 'MAX_HEAP=256M'],
           'sleep': 30,
           'healthcheck': 9042]],
        ['KAFKA': [
            'image': 'confluentinc/cp-kafka:5.3.1',
            'sleep': 300,
            'healthcheck': 9092,
            'resources': ['limits': ['memory': "1Gi"]],
            'env': ['KAFKA_ADVERTISED_HOST_NAME=%%OWNHOSTNAME',
                   'KAFKA_ZOOKEEPER_CONNECT=%%ZOOKEEPER:2181',
                   'KAFKA_DELETE_TOPIC_ENABLE=true',
                   'KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://%%OWNHOSTNAME:9092',
                   'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT',
                   'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
                   'KAFKA_BROKER_ID=1']
            ]
        ],
        ['LDAP': [
            'image': 'stratio/ldap-docker:0.2.0',
            'env': [
                  'LDAP_SCHEMA=rfc2307',
                  'LDAP_HOSTNAME=%%OWNHOSTNAME',
                  'HOSTNAME=%%OWNHOSTNAME.cd',
                  'LDAP_ORGANISATION=Stratio',
                  'LDAP_DOMAIN=stratio.com',
                  'LDAP_ADMIN_PASSWORD=stratio'],
            'sleep': 30,
            'healthcheck': 389]],
	    ['CHROME': [
	        'image': 'selenium/standalone-chrome-debug:3.141.59',
            'resources': ['limits': ['memory': "2Gi"]],
		    'healthcheck': 4444]],
        ['UBUNTU': [
           'image': 'stratio/ubuntu-base:16.04',
           'cmd': '/usr/sbin/sshd -D -e',
           'healthcheck': 22]],
        ['VAULT': [
           'image': 'vault:0.6.2',
           'env': [
              'VAULT_DEV_ROOT_TOKEN_ID=stratio',
              'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200'],
           'sleep': 5,
           'healthcheck': 8200]],
    ]
    
    ITPARAMETERS = """
	    | -DFORCE_BROWSER=%%CHROME
        | -DMONGO_HOST=%%MONGODB
        | -DCASSANDRA_HOST=%%CASSANDRA
        | -DES_NODE=%%ELASTIC
        | -DES_CLUSTER=elasticsearch
        | -DZOOKEEPER_HOSTS=%%ZOOKEEPER:2181
        | -DSECURIZED_ZOOKEEPER=false
        | -DWAIT=1
        | -DAGENT_LIST=1,2
        | -DPROGLOOP=2
        | -DKAFKA_HOSTS=%%KAFKA:9092
        | -DSSH=%%UBUNTU
        | -DSLEEPTEST=1
        | -DLDAP_USER=admin
        | -DLDAP_BASE=dc=stratio,dc=com
        | -DLDAP_PASSWORD=stratio
        | -DLDAP_SSL=false
        | -DVAULT_URL=%%VAULT
        | -DVAULT_PROTOCOL=http://
        | -DVAULT_HOST=%%VAULT
        | -DVAULT_TOKEN=stratio
        | -DLDAP_URL=%%LDAP
        | -DINCLUDE=1
        | -DlogLevel=DEBUG
        | -DLDAP_PORT=389""".stripMargin().stripIndent()
    
    DEV = { config ->        
        doCompile(config)

        parallel(UT: {
            doUT(config)
        }, IT: {
            doIT(config)
        }, failFast: config.FAILFAST)

        doPackage(config)
    
        parallel(QC: {
            doStaticAnalysis(config)
        }, DEPLOY: {
            doDeploy(config)
        }, failFast: config.FAILFAST)

     }
}
