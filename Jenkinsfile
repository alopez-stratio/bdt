@Library('libpipelines') _

hose {
    EMAIL = 'qa'
    DEVTIMEOUT = 60
    RELEASETIMEOUT = 60
    BUILDTOOL = 'maven'
    BUILDTOOL_IMAGE = "stratio/qa-builder:0.5.0"
    BUILDTOOL_MEMORY_LIMIT = '4Gi'
    BUILDTOOL_MEMORY_REQUEST = '2Gi'
    BUILDTOOL_CPU_LIMIT = '4'
    BUILDTOOL_CPU_REQUEST = '2'
    DEPLOYONPRS = true

    ITSERVICES = [
        ['ZOOKEEPER': [
           'image': 'zookeeper:3.5.7',
           'env': [],
           'sleep': 30,
           'healthcheck': 2181]],
        ['MONGODB': [
           'image': 'stratio/mongo:3.0.4',
           // The library only turns 'healthcheck' into a real TCP probe when 'sleep' is also
           // present (continuous-delivery-tng, cd/Util.groovy:619). Without it the pod reports
           // Ready as soon as the container starts, which says nothing about mongod -- the same
           // blind gate that let a dead Cassandra through. 'sleep' is a budget, not a delay: the
           // pod is Ready the moment 27017 answers.
           'sleep': 60,
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
           // 'sleep' is not a delay: the library turns it into failureThreshold = sleep/10 with a
           // 10s period, for both probes (cd/Util.groovy:619). At 30 the liveness probe gave the
           // container 30 seconds from start to open 9042 -- but Cassandra needs 60-100s to get
           // there in CI (12s locally). So any restart was fatal: the probe killed it again
           // before it finished booting, and it never came back. 180 gives it 18 attempts.
           'sleep': 180,
           // Two separate things here. The limit: without it the container inherits the library
           // default of 512Mi (cd/Util.groovy:574), and Cassandra idles at 443MiB and grows past
           // 512Mi once a scenario inserts rows, so the JVM was OOM-killed. The request: the
           // library defaults it to 256Mi (cd/Util.groovy:571), which leaves the container
           // Burstable and using ~2x what it asked for -- first in line when the node is tight.
           // Requesting 1Gi covers the real footprint.
           //
           // None of this is visible when it goes wrong: Cassandra is the only JVM service here
           // whose process is not PID 1. The entrypoint forks it through /etc/init.d/cassandra
           // and stays alive on a `tail -f`, so the container keeps running, the pod stays Ready
           // and 9042 just refuses.
           'resources': ['requests': ['memory': "1Gi"], 'limits': ['memory': "2Gi"]],
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
            // See MONGODB above: no 'sleep' meant no probe on the grid port either.
            'sleep': 60,
		    'healthcheck': 4444]],
        ['UBUNTU': [
           'image': 'stratio/ubuntu-base:16.04',
           'cmd': '/usr/sbin/sshd -D -e',
           // See MONGODB above: no 'sleep' meant no probe on sshd either.
           'sleep': 60,
           'healthcheck': 22]],
        ['VAULT': [
           'image': 'vault:0.6.2',
           'env': [
              'VAULT_DEV_ROOT_TOKEN_ID=stratio',
              'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200'],
           // failureThreshold is sleep/10 floored at 1, so the previous value of five gave
           // Vault a single 10-second attempt to open 8200 before the liveness probe
           // restarted it. Thirty gives it three.
           'sleep': 30,
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
            // Without these, the scanner treats src/test as production source: its ~8.4k lines count
            // as ncloc with no coverage data attached, which drags the project coverage down and adds
            // test-only code smells to the quality gate.
            doStaticAnalysis(
                conf: config,
                sonarAdditionalProperties: [
                    "sonar.sources": "src/main/java",
                    "sonar.tests": "src/test/java"
                ]
            )
        }, DEPLOY: {
            doDeploy(config)
        }, failFast: config.FAILFAST)

     }
}
