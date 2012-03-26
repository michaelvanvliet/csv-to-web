/**

	This is an example how to create a simple chemical compound database using:
	
	- Groovy (http://groovy.codehaus.org)
	- Gradle (http://www.gradle.org
	- Ratpack (https://github.com/bleedingwolf/Ratpack)
	- GMongo (https://github.com/poiati/gmongo)
	- MongoDB (http://www.mongodb.org)
	
	 ____  ____  ____  ____  ____  ____ 
	||S ||||i ||||m ||||p ||||l ||||e ||
	|/__\||/__\||/__\||/__\||/__\||/__\|
		 ____  ____  ____  ____  ____  ____  ____  ____ 
		||C ||||h ||||e ||||m ||||i ||||c ||||a ||||l ||
		|/__\||/__\||/__\||/__\||/__\||/__\||/__\||/__\|
			 ____  ____  ____  ____  ____  ____  ____  ____ 
			||C ||||o ||||m ||||p ||||o ||||u ||||n ||||d ||
			|/__\||/__\||/__\||/__\||/__\||/__\||/__\||/__\|
				 ____  ____  ____  ____  ____  ____  ____  ____ 
				||D ||||a ||||t ||||a ||||b ||||a ||||s ||||e ||
				|/__\||/__\||/__\||/__\||/__\||/__\||/__\||/__\|
	
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
@Grab('com.gmongo:gmongo:0.8')
@Grab('commons-fileupload:commons-fileupload:1.2.2')
@Grab('commons-io:commons-io:1.3.2')
import com.gmongo.GMongo 
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.util.Random


// config
def mdbHost 		= 'localhost' // host where mongoDB is running
def mdbPort 		= 27017 // port where mongoDB listens
def mdbDatabase 	= 'simpleCompoundDatabase' // name of the database
def bootstrap 		= true // true/false
def rpPort		= 8080 //define the (Ratpack) http port to run on
def exportsFolder	= 'exports'
 
 
// connect to the database
def gmongo	= new GMongo	("${mdbHost}:${mdbPort}")
def db 		= gmongo.getDB	("${mdbDatabase}")


// bootstrap
if (bootstrap){
	db.compounds.drop() //clear old stuff

	def rand  = new Random()

	5.times { cid ->
		def elements = 'C' + rand.nextInt(7) + 'H' + rand.nextInt(7) + 'O' + rand.nextInt(7)
		db.compounds << [	
			id: cid, 
			InChI: 'InChI=1S/' + elements + '/' + cid,
			elements: elements,
			created: new Date().time,
			updated: new Date().time
		]
	}
}

// define Ratpack settings
set 'templateRoot', 'templates'
set 'port', rpPort

// homepage
get("/") { render "index.html", [compoundCount: db.compounds.find().size()] }

// the a list of all compounds in the database
get("/list") { respond(db.compounds.find()) }

// a single (full details) compound by ID
get("/compound/:id") { respond(findCompoundById(db, urlparams.id as int)) }

// a list of compounds by Elemental Composition  (uses regular expressions)
get("/elements/:elements") { respond(findCompoundByElements(db, urlparams.elements as String)) }

// search page
get("/search") { render "search.html", [templateVars: [:]] }

register(["get", "post"], "/search/:method") {

	def templateVars = [:]
	def response

	switch (urlparams.method){
		case 'searchById'			:	if (params.compound_id){ response = respond( findCompoundById(db, params.compound_id as int)) }; break;
		case 'searchByElements'		:	if (params.elements) { response = respond( findCompoundByElements(db, params.elements as String)) }; break;
		case 'searchByInChI'		:	if (params.inchi) { response = respond( findCompoundByInchi(db, params.inchi as String)) }; break;										
	}
	
	// prepare the variables for the template
	templateVars[urlparams.method] 		= response
	templateVars['urlparams'] 			= urlparams
	templateVars['params'] 				= params
	
	render "search.html", [templateVars: templateVars]
}

// add compounds page
get("/import") { render "import.html", [templateVars: [:]] }

post("/import") {

	boolean isMultipart = ServletFileUpload.isMultipartContent(request)
	if (isMultipart) {
		// Create a factory for disk-based file items
		def factory = new DiskFileItemFactory()

		// Create a new file upload handler
		def upload = new ServletFileUpload(factory)

		// Parse the request
		List files = upload.parseRequest(request)
		def compoundData = ''
			
		files.each {
			compoundData += it.getString()
		}
		
		def lines = compoundData.split('\n')
		def header = lines[0].split(',')
		
		//check if the the header contains at least an ID.
		if (header[0] == '"id"'){
		
			// iterate over the lines from the file to import
			lines.each { line ->
				
				//init empty compound
				def compound = [:]
				
				line.split(",").eachWithIndex { rowValue, columnIndex ->
					compound[header[columnIndex]] = rowValue
				} 
				
				db.compounds << compound
			}
		}		
	}

	render "import.html", [templateVars: [:]] 
}

// register exportsFolder
get("/" + exportsFolder + "/:file"){
	return new File(exportsFolder + '/' + urlparams.file).text
}
	
//export compounds
get("/export") {
	
	if (params.doExport){
	
		//fetch all compounds
		def compounds = formatResponse(db.compounds.find())

		//prepare the headers
		def headers = [].toList()	
		compounds['results'].each {
			headers = it.keySet().toList() + headers
		}
		headers = headers.unique()
	
		// add the header to the CSV
		def csvOut = '"id","' + headers.findAll { it != 'id' }.sort { a,b -> a <=> b}.join('","') + "\"\n"

		//iterate over compounds
		compounds['results'].each { compound ->
		
			csvOut += compound.id
		
			//iterate over all available headers
			headers.findAll { it != 'id' }.sort { a,b -> a <=> b}.each { header ->			
				csvOut +=  ',"' + compound."${header}" + '"'
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
	return ['results': result.collect { it.findAll { it.key != '_id' } }.sort { a,b -> a.id <=> b.id}, 'count': result.size()]
}

// find compound by id
private findCompoundById(db, int compoundId){
	return db.compounds.find(id: compoundId)
}

// find compounds by elemental composition
private findCompoundByElements(db, String elements){
	return db.compounds.find(elements: ~"${elements.toUpperCase()}")
}

// find compounds by inchi
private findCompoundByInchi(db, String inchi){
	return db.compounds.find(InChI: ~"${inchi}")
}
