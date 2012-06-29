/**

	Create a search website/API from a CSV file
	
	- Groovy (http://groovy.codehaus.org)
	- Gradle (http://www.gradle.org
	- Ratpack (https://github.com/bleedingwolf/Ratpack)
	- GMongo (https://github.com/poiati/gmongo)
	- MongoDB (http://www.mongodb.org)
	
	Copyright 2012 Michael van Vliet, Netherlands Metabolomics Centre (NMC) and Netherlands Bioinformatics Centre (NBIC) 

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.	
 **/
  
  
// imports
@Grab('com.gmongo:gmongo:0.9.5')
@Grab('commons-fileupload:commons-fileupload:1.2.2')
@Grab('commons-io:commons-io:1.3.2')
import com.gmongo.GMongo 
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.util.Random


// config
def mdbHost				= 'localhost' // host where mongoDB is running
def mdbPort				= 27017 // port where mongoDB listens
def mdbDatabase			= 'csv2web' // name of the database
def bootstrap			= false // true/false
def clearAtStartup		= false //true/false
def rpPort				= 8080 //define the (Ratpack) http port to run on
def exportsFolder		= 'exports'
def reservedProperties	= ['_id','Rid','Created','Modified']
 
 
// connect to the database
def gmongo	= new GMongo	("${mdbHost}:${mdbPort}")
def db 		= gmongo.getDB	("${mdbDatabase}")

//check if we need to clear the database before starting
if (clearAtStartup == true){
	db.records.drop() //clear old stuff
}

// bootstrap
if (bootstrap){
	
	def rand  = new Random()
		
	5.times { Rid ->
		def elements = 'C' + rand.nextInt(7) + 'H' + rand.nextInt(7) + 'O' + rand.nextInt(7)
		upsertRecord(db, [
			InChI: 'InChI=1S/' + elements + '/' + Rid, 
			elements: elements, 
			contributor: 'myEmployer',
			owner: 'me',
			database: mdbDatabase, 
			host: mdbHost	]
		)
	}
}

// define Ratpack settings
set 'templateRoot', 'templates'
set 'port', rpPort 


/** API Calls **/
// the a list of all records in the database
get("/api/list") { 
	response.setContentType('application/json')	
	respond(db.records.find()) }
post("/api/list") {
	response.setContentType('application/json')	
	return respond(db.records.find()) 
}

//retrieve available labels from db
get("/api/labels") { 
	response.setContentType('application/json')	
	respond(findAllHeaders(db, reservedProperties))
}
post("/api/labels") {
	response.setContentType('application/json')
	respond(findAllHeaders(db, reservedProperties)) 
}

// a single (full details) record by ID
get("/api/record/:Rid") { 
	response.setContentType('application/json')
	respond(findRecordByRid(db, urlparams.Rid as int))
}
post("/api/record") { 
	response.setContentType('application/json')	
	respond(findRecordByRid(db, params.Rid as int))
}

// api search by label
get("/api/search/:label/equals/:value"){ 
	response.setContentType('application/json')	
	respond(findRecordByLabel(db, urlparams.label, urlparams.value, false))
}
get("/api/search/:label/regex/:value"){
	response.setContentType('application/json')	
	respond(findRecordByLabel(db, urlparams.label, urlparams.value, true))
}
post("/api/search"){	
	response.setContentType('application/json')	
	respond(findRecordByLabel(db, params.label, params.value, (params.regex as int == 1 ? true : false)))
}
post("/api/searchbetween"){
	response.setContentType('application/json')
	respond(findRecordByLabelBetween(db, params.label, params.valueA as float, params.valueB as float))
}


/** GUI **/
// homepage
get("/") { render "index.html", [recordCount: db.records.find().size()] }

// published contents
get("/publish") {
	
	def records = db.records.find()
	def recordIndexHtml = new File('templates/_header.html').text
	
	//generate record pages (/public/:Rid)
	records.each { record ->
		
		//create/clear new record file
		try {
			def cFile = new File('public/' + record['Rid'] + '.html')
				cFile.write(recordToHtml(record))
				
			// add created record file to index
			recordIndexHtml += '<a href="/public/' + record['Rid'] + '">Record ' + record['Rid'] + '</a><br />\n'
			
		} catch (e) {
			//TODO: handle error(s)
		}
	}
	
	//generate record index page (/public/index.html)
	recordIndexHtml += new File('templates/_footer.html').text //add footer
	
	def RidxFile = new File('public/index.html')
		RidxFile.write(recordIndexHtml)
	 
	render "publish.html" 
}
get("/public") { return new File('public/index.html').text }
get("/public/:Rid") { return new File('public/' + urlparams.Rid + '.html').text }


// search page
get("/search") { render "search.html", [templateVars: ['headers': findAllHeaders(db, reservedProperties)]] }

register(["get", "post"], "/search/:method") {

	def templateVars = [:]
	def response = []

	switch (urlparams.method){
		case 'listAllRecords'		:	response = findAllRecords(db)
										break
										
		default						:	response = findRecordByLabel(db, params.searchBy, params.searchValue as String, true)										
	}
	
	// prepare the variables for the template
	templateVars[urlparams.method] 		= response
	templateVars['urlparams'] 			= urlparams
	templateVars['params'] 				= params
	templateVars['headers']				= findAllHeaders(db, reservedProperties)
	
	render "search.html", [templateVars: templateVars]
}

// add records page
get("/import") { render "import.html" }

post("/import") {

	boolean isMultipart = ServletFileUpload.isMultipartContent(request)
	if (isMultipart) {
		// Create a factory for disk-based file items
		def factory = new DiskFileItemFactory()

		// Create a new file upload handler
		def upload = new ServletFileUpload(factory)

		// Parse the request
		List files = upload.parseRequest(request)
		
		// Merge file(s)
		def recordData = ''	
		files.each { recordData += it.getString() }
		
		// Create lists with the lines and read the header
		def lines = recordData.replaceAll('\r\n','\n').replaceAll('\r','\n').split('\n')
		def header = lines[0].split("\t")
		
		// iterate over the lines from the file to import
		lines.each { line ->
							
			//init empty record
			def record = [:]
			
			line.split("\t").eachWithIndex { rowValue, columnIndex ->
				if (header[columnIndex] != rowValue){ // make sure we skip the header when importing
					
					if (rowValue){
						//see if we have to trim the text qualifiers from the value
						if (rowValue[0] == '"' && rowValue[-1] == '"'){
							rowValue = rowValue[1..-2] // trim the first and last "
						}
					} 
					
					record[header[columnIndex]] = rowValue
				}
			}
			
			if (record != [:]){		
				//update or insert this record
				upsertRecord(db, record)
			}
				
		}		
	}

	render "import.html" 
}

// register exportsFolder
get("/" + exportsFolder + "/:file"){
	return new File(exportsFolder + '/' + urlparams.file).text
}
	
//export records
get("/export") {
	
	if (params.doExport){
	
		//fetch all records
		def records = formatResponse(db.records.find())

		//prepare the headers
		def headers = findAllHeaders(db, reservedProperties).sort { a,b -> a <=> b}
	
		// add the header to the CSV
		def csvOut = "Rid\t" + headers.join("\t") + "\n"

		//iterate over records
		records['results'].each { record ->
		
			csvOut += record.Rid
		
			//iterate over all available headers
			headers.each { header ->			
				csvOut +=  "\t" + record."${header}"
			}
			
			csvOut +=  "\n"
		}
		
		//write export to file
		new File(exportsFolder).mkdirs()
		new File(exportsFolder + '/export.' +  new Date().time + '.csv') << csvOut		
	}	
	
	render "export.html", [exportDirectory: new File(exportsFolder)] 
}

/**
	Methods for fetching data, data manipulation and fetch response
 **/

// prepare and send the actual response
private respond(response){ 
	return new groovy.json.JsonBuilder( formatResponse(response) ).toPrettyString()
}

// define a uniform layout of the response
private formatResponse(result) {
	
	def formattedResults = ''
	
	switch(result.getClass().toString()){
		case 'class com.mongodb.DBCursor' : formattedResults = result.collect { it.findAll { it.key != '_id' } }.sort { a,b -> a.id <=> b.id }
											break
		default : formattedResults = result
	}
	
	return ['results': formattedResults, 'count': result.size()]
}

// find all records
private findAllRecords(db){
	return db.records.find()
}

// find record by id
private findRecordByRid(db, int Rid){
	return db.records.find(Rid: Rid)
}

// find records by a label
private findRecordByLabel(db, String label, labelValue, useRegex = false){
	
	def findHash = [:]
	
	try {
	
		//force label to correct CamelCase
		label = toCamelCase(label)
		
		//overwrite to use regex when looking for the Rid and force it to an interger
		if (label == 'Rid') { 
			useRegex = false
			labelValue = labelValue as int
		}
		
		//prepare search hash
		if (useRegex){ findHash["${label}"] = ~"(?ix)${labelValue}" } else { findHash["${label}"] = labelValue } 
	} catch (e) {
		findHash['Rid'] = -1 //return nothing
	}
	
	return db.records.find(findHash)
}

private findRecordByLabelBetween(db, String label, float labelValueA, float labelValueB){

	def recordsInBetween = []
	
	db.records.find().each { record ->
		
		float searchValue = record."${label}"
		float min = labelValueA
		float max = labelValueB
		
		if (searchValue  >= min && searchValue <= max){
			Map c = record
			recordsInBetween.add(c)
		}
	}
	
	return recordsInBetween 
}

// retrieve a unique list of headers used to label records
private findAllHeaders(db, headersToSkip = []){

	//prepare the headers
	def headers = [].toList()
	db.records.find().each { 
		headers = it.keySet().toList() + headers
	}
	
	return headers.unique().findAll { headersToSkip.count(it) != 1 }.sort { a,b -> a <=> b }
}

// insert or update a record
private upsertRecord(db, HashMap record){
	
	//some characters in property names give problems retrieving data, we force labels to CamelCase when it has a space, - or a _
	def tempRecord = [:]
	record.each { propertyKey, propertyValue ->
		tempRecord[toCamelCase(propertyKey)] = propertyValue
	}
	record = tempRecord
	
	//there are some reserved record properties (e.g Rid, Created, Modified)
	try {
		// if we cannot find a record with this id, we set the Created to match the Modified property
		if (record['Rid'] == null || !findRecordByRid(db, record['Rid'] as int)){
			//TODO: make this look for the highest Rid and increment this with one, the way we do it now only works because we start Rid with 0
			record['Rid']		= (db.records.find().size() ?: 0) as int //force the Rid to auto-increment 
			record['Created'] = new Date().time
		} 
		
		// update the Modified timestamp to track when changes are made to the record
		record['Modified'] = new Date().time
		
		// send changes to the database
		db.records.update([Rid: record['Rid']], [$set: record], true)
	} catch(e) {
		//TODO: Add a real logger
		println 'Error saving the record: ' + e
		return false
	}	
	
	return true
}

private toCamelCase(String label = ''){
	if (label){ // not null or '' 
		label = label.replaceAll(/(\w)(\w*)/) { wholeMatch, initialLetter, restOfWord -> initialLetter.toUpperCase() + restOfWord }
		label = label.split('_').collect { it[0].toUpperCase() + it.substring(1) }.join('')
		label = label.split('-').collect { it[0].toUpperCase() + it.substring(1) }.join('')
	}

	return label
}

private recordToHtml(record){
	
	def recordHtml = ''
	
	// add html header
	recordHtml += new File('templates/_header.html').text
	
	// add content placeholder
	recordHtml += '\n\n|||BODY|||\n\n'
	
	// add html footer
	recordHtml += new File('templates/_footer.html').text
	
	def html = ''
	// add record Rid
	html += '<h1>Record ' + record['Rid'] + '</h1>\n'
	
	// add nice table with properties
	record.each { property, value ->
		html += '\t<b>' + property + '</b>:\t' + value + '<br />\n'
	}
	
	recordHtml = recordHtml.replace('|||BODY|||', html)
	
	return recordHtml	
}