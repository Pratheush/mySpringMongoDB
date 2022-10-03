package com.mylearning.myspringmongodb.repository;

import com.mylearning.myspringmongodb.collection.Person;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends MongoRepository<Person,String> {
    List<Person> findByFirstNameStartsWith(String name);

    //List<Person> findByAgeBetween(Integer min, Integer max);

    @Query(value = "{ 'age' : { $gt : ?0, $lt : ?1}}",
            fields = "{addresses:  0}") // 0 means I don't need 1 means I need. here mentioning that i don't need this field and rest of field i will get
    List<Person> findPersonByAgeBetween(Integer min, Integer max);
}
