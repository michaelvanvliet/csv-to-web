/**
 ___   ___  ___    ____  ____ 
/ __) / __)/ __)  (  _ \(  _ \
\__ \( (__( (__    )(_) )) _ <
(___/ \___)\___)()(____/(____/
                                 	
	This is an example how to create a simple chemical compound database (SCC.DB) using:
	Groovy (http://groovy.codehaus.org)
	Ratpack (https://github.com/bleedingwolf/Ratpack)
	MongoDB (http://www.mongodb.org/)
	
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
import com.gmongo.GMongo
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.util.Random


// config
def mdbHost 		= 'localhost' // host where mongoDB is running
def mdbPort 		= 27017 // port where mongoDB listens
def mdbDatabase 	= 'simpleCompoundDatabase' // name of the database
def bootstrap 		= true // true/false
def rpPort			= 8080 //define the (Ratpack) http port to run on
 
 
// connect to the database
def gmongo	= new GMongo	("${mdbHost}:${mdbPort}")
def db 		= gmongo.getDB	("${mdbDatabase}")


// bootstrap
if (bootstrap){
	db.compounds.drop() //clear old stuff

	def rand  = new Random()

	100.times { cid ->
		def elements = 'C' + rand.nextInt(7) + 'H' + rand.nextInt(7) + 'O' + rand.nextInt(7)
		db.compounds << [id: cid, InChI: 'InChI=1S/' + elements + '/' + cid, elements: elements]
	}
}

// define Ratpack settings
set 'templateRoot', 'templates'
set 'port', rpPort

// homepage
get("/") { render "index.html" }

// the a list of all compounds in the database
get("/list") { respond(db.compounds.find()) }

// a single (full details) compound by ID
get("/compound/:id") { respond(findCompoundById(db, urlparams.id as int)) }

// a list of compounds by Elemental Composition  (uses regular expressions)
get("/elements/:elements") { respond(findCompoundByElements(db, urlparams.elements as String)) }

// search page
get("/search") { render "search.html", [results: [:]] }

register(["get", "post"], "/search/:method") {

	def templateVars = [:]
	def response

	switch (urlparams.method){

		//ID
		case 'searchById'			:	if (params.compound_id){ 
											response = respond(
												findCompoundById(db, params.compound_id as int)
											) 
										}
										break;

		//ELEMENTS
		case 'searchByElements'		:	if (params.elements) { 
											response = respond(
												findCompoundByElements(db, params.elements as String)
											) 
										}
										break;
										
		//INCHI
		case 'searchByInChI'		:	if (params.inchi) { 
											response = respond(
												findCompoundByInchi(db, params.inchi as String)
											) 
										}
										break;										
	}
	
	// prepare the variables for the template
	templateVars[urlparams.method] 	= response
	templateVars['urlparams'] 		= urlparams
	templateVars['params'] 			= params
	
	render "search.html", [templateVars: templateVars]
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
	return result.collect { it.findAll { it.key != '_id' } }.sort { a,b -> a.id <=> b.id}
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