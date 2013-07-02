CSV to Web
=================================

It turns a CSV file into a search website/API

> It uses Ratpack to serve the HTTP stuff, Groovy and GMongo to do the magic and MongoDB persists the data. Gradle is only required to get Ratpack running on you machine.

Requirements
------------
Groovy 1.8+ (http://groovy.codehaus.org)

Gradle (http://gradle.org/downloads)

Ratpack (https://github.com/bleedingwolf/Ratpack)

GMongo (https://github.com/poiati/gmongo)

MongoDB 1.8+ (http://www.mongodb.org/)


Getting Started
---------------

Just run:

	$ ratpack csv2web.groovy

This should start an instance of Jetty. Output looks like this:

	842 [main] INFO com.bleedingwolf.ratpack.RatpackApp - Starting Ratpack app with config: {port=8080, templateRoot=templates}
	880 [main] INFO org.mortbay.log - Logging to org.slf4j.impl.SimpleLogger(org.mortbay.log) via org.mortbay.log.Slf4jLog
	939 [main] INFO org.mortbay.log - jetty-6.1.26
	1068 [main] INFO org.mortbay.log - Started SocketConnector@0.0.0.0:8080

Now open a browser and enter: http://localhost:8080

Credits
-------

The Netherlands Metabolomics Centre (NMC) [http://www.metabolomicscentre.nl](http://www.metabolomicscentre.nl)

The Netherlands Bioinformatics Centre (NBIC) [http://www.nbic.nl](http://www.nbic.nl)

