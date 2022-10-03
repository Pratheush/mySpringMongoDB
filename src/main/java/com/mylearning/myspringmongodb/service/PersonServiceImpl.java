package com.mylearning.myspringmongodb.service;

import com.mylearning.myspringmongodb.collection.Person;
import com.mylearning.myspringmongodb.exceptions.PersonAlreadyExistException;
import com.mylearning.myspringmongodb.repository.PersonRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PersonServiceImpl implements PersonService{
    //@Autowired
    private PersonRepository personRepository;

    //@Autowired
    private MongoTemplate mongoTemplate;

    public PersonServiceImpl(PersonRepository personRepository,MongoTemplate mongoTemplate) {
        this.personRepository = personRepository;
        this.mongoTemplate= mongoTemplate;
    }



    @Override
    public String save(Person person) {

        Optional<Person> optionalPerson=personRepository.findById(person.getPersonId());
        if (optionalPerson.isPresent()) {
            throw new PersonAlreadyExistException("Person Alread Exist");
        }
        Person savedPerson1=personRepository.save(person);
        return "Person saved with id:"+savedPerson1.getPersonId();
        //return personRepository.save(person).getPersonId();
    }

    @Override
    public List<Person> getPersonStartWith(String name) {
        return personRepository.findByFirstNameStartsWith(name);
    }

    @Override
    public void delete(String id) {
        personRepository.deleteById(id);
    }

    @Override
    public List<Person> getByPersonAge(Integer minAge, Integer maxAge) {
        return personRepository.findPersonByAgeBetween(minAge,maxAge);
    }

    @Override
    public Page<Person> search(String name, Integer minAge, Integer maxAge, String city, Pageable pageable) {

        // org.springframework.data.mongodb.core.query.Query
        Query query = new Query().with(pageable); // this Query is from core not from repository in spring framework.
        List<Criteria> criteria = new ArrayList<>();

        if(name !=null && !name.isEmpty()) {   // criteria where name is not null and not empty at that time we are adding the criteria we need all the  data where its firstname
            criteria.add(Criteria.where("firstName").regex(name,"i")); //where its firstname should match with the name that we provide and it should be encase-sensitive i.e. what i is telling.
        }

        if(minAge !=null && maxAge !=null) { // criteria where minAge is not null and maxAge is not null at that time we are adding the criteria we need all the  data
            criteria.add(Criteria.where("age").gte(minAge).lte(maxAge));// where its age is greater than equalto minage and less than equalto maxage
        }

        if(city !=null && !city.isEmpty()) {  // criteria where city is not null not empty at that time we are adding the criteria we need all the  data
            criteria.add(Criteria.where("addresses.city").is(city));// where its addresses.city is equal to city given.
        }

        if(!criteria.isEmpty()) { // if criteria is not empty then add Criteria to querry and add all the above criteria using and operator.
            query.addCriteria(new Criteria() // attaching the Criteria to query
                    .andOperator(criteria.toArray(new Criteria[0]))); //x.toArray(new String[0]); Returns an array containing all of the elements in this list in proper sequence (from first to last element); the runtime type of the returned array is that of the specified array.
                                                                       //  Suppose x is a list known to contain only strings. The following code can be used to dump the list into a newly allocated array of String:
                                                                        //     String[] y = x.toArray(new String[0]);
                                                                        //
                                                                        //Note that toArray(new Object[0]) is identical in function to toArray().
        }

        // we have to pass the content we have to pass the pageable then we need to find the supplier as well what is the total value of that particular list
        Page<Person> people = PageableExecutionUtils.getPage(
                mongoTemplate.find(query, Person.class // here in getPage we are going to get the entire list of person using mongoTemplate.find(query,Person.class) so that we can paginate
                ), pageable,// we are passing the pageable
                () -> mongoTemplate.count(query.skip(0).limit(0),Person.class)); // passing the supplier
        return people;
    }

    // we are using Document from org.bson.Document because mongodb stores the data in the binary json format
    @Override
    public List<Document> getOldestPersonByCity() {
        // the access to the city is only by addresses
        // city is part of address and addresses part of person so
        // we need to flatten out that addresses rather than list of addresses inside
        // person what i need is to flatten out i.e. unwrapped the operation so that i can access the addresses
        // so its called an UnwindOperation. so i am going to do UnwindOperation on the address so directly i can
        // access addresses from the person after that sort all the data based on age once the sorting is done
        // i will group all the data based on the city once that is done i will get the oldest person by city.
        UnwindOperation unwindOperation
                = Aggregation.unwind("addresses"); // defining  where i need to do unwind-operation
        SortOperation sortOperation
                = Aggregation.sort(Sort.Direction.DESC,"age");
        GroupOperation groupOperation
                = Aggregation.group("addresses.city")      // grouping operation
                .first(Aggregation.ROOT)                        // GroupOperationBuilder:: // here i am taking the first root so using first() to get the aggregated document root so using Aggregation.ROOT since i have already sorted according to age i can get the first documented root data element
                .as("oldestPerson");    // defining the alias

        // here this is aggregation and this will take the varags of the operations. I need to do first unwindOperation then sortOperation and then groupOperation
        Aggregation aggregation
                = Aggregation.newAggregation(unwindOperation,sortOperation,groupOperation);

        List<Document> person
                = mongoTemplate.aggregate(aggregation, Person.class,Document.class).getMappedResults();
        return person;
    }

    @Override
    public List<Document> getPopulationByCity() {

        UnwindOperation unwindOperation
                = Aggregation.unwind("addresses");
        GroupOperation groupOperation
                = Aggregation.group("addresses.city")
                .count().as("popCount");
        SortOperation sortOperation
                = Aggregation.sort(Sort.Direction.DESC, "popCount");

        // here we define what data we need back. we don't need the entire object with all the data back in response
        // i just need some particular fields so useing ProjectionOperation I can define what data or fields i can get in respone
        ProjectionOperation projectionOperation
                = Aggregation.project()
                .andExpression("_id").as("city") //we are going to add all the expressions here... here city should come as id
                .andExpression("popCount").as("count") // popCount should come as count and
                .andExclude("_id"); // and actual id whatever is defined that should not come

        Aggregation aggregation
                = Aggregation.newAggregation(unwindOperation,groupOperation,sortOperation,projectionOperation);

        List<Document> documents
                = mongoTemplate.aggregate(aggregation,
                Person.class,
                Document.class).getMappedResults();
        return  documents;
    }
}
