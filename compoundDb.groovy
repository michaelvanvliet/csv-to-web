@Grab('com.gmongo:gmongo:0.8')
import com.gmongo.GMongo

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.util.Random

def gmongo = new GMongo('localhost:27017')
def db = gmongo.getDB('simpleCompoundDatabase')

//prepare and send the actual response
def respond(response){
	return new groovy.json.JsonBuilder(
		formatResponse(response)
	).toPrettyString()
}

//define a uniform layout of the response
def formatResponse(result) {
	return result.collect { 
		it.findAll { it.key != '_id' }	// ignore the MongoDB properties
	}.sort { a,b -> a.id <=> b.id}		// and sort by id
}

//inject some test compounds
db.compounds.drop() //clear old stuff

def rand  = new Random() //

10000.times { cid ->
	def elements = 'C' + rand.nextInt(7) + 'H' + rand.nextInt(7) + 'O' + rand.nextInt(7)
	db.compounds << [id: cid, InChI: 'InChI=1S/C' + elements + '/' + cid, elements: elements]
}

//tell ratpack where the templates are
set 'templateRoot', 'templates'

//returns homepage
get("/") { render "index.html" }

//returns the a list of all compounds in the database
get("/list") { respond(db.compounds.find()) }

//returns a single (full details) compound by ID
get("/compound/:id") { respond(db.compounds.find(id: urlparams.id as int)) }

//returns a list of compounds by InChI (uses regular expressions)
get("/inchi/:inchi") { respond(db.compounds.find(InChI: ~"${urlparams.inchi}")) }

//returns a list of compounds by Elemental Composition  (uses regular expressions)
get("/elements/:elements") { respond(db.compounds.find(elements: ~"${urlparams.elements.toUpperCase()}")) }

//error handling
get("") {
    "Wrong InChI caught!"
}
