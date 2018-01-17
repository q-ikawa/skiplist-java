package hiv.arc.collections.skiplist;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

import org.junit.Test;

public class SkipListTest {

	class PersonId {
		int id;
		PersonId(int id){
			this.id = id;
		}
		@Override
		public int hashCode() {
			return id;
		}
		@Override
		public boolean equals(Object obj) {
			if( obj instanceof PersonId){
				return id == ((PersonId)(obj)).id;
			}
			return false;
		}
	}
	class PersonName{
		String value;
		PersonName(String value){
			this.value = value;
		}
	}
	class Person{
		PersonName name;
		Priority priority;
		
		Person( PersonName name, Priority priority){
			this.name = name;
			this.priority = priority;
		}
		
		@Override
		public String toString() {
			return name.value;
		}
	}
	class Priority{
		double value;
		Priority(double value){
			this.value = value;
		}
	}
	
	@Test
	public void test() {
		SkipList<PersonId, Person> list = new SkipList<PersonId,Person>();
		Person alice = new Person(new PersonName("Alice"),new Priority(10));
		Person bob = new Person(new PersonName("Bob"),new Priority(20));
		Person chuck = new Person(new PersonName("Chuck"),new Priority(30));
		Person dan = new Person(new PersonName("Dan"),new Priority(40));
		Person erin = new Person(new PersonName("Erin"),new Priority(50));
		
		list.put(new PersonId(1),alice,alice.priority.value);
		list.put(new PersonId(2),bob,bob.priority.value);
		list.put(new PersonId(3),chuck,chuck.priority.value);
		list.put(new PersonId(4),dan,dan.priority.value);
		list.put(new PersonId(5),erin,erin.priority.value);
		
		System.out.println(list.deepToString());
		
		assertEquals(alice, list.at(0).get());
		assertEquals(bob, list.at(1).get());
		assertEquals(chuck, list.at(2).get());
		assertEquals(dan, list.at(3).get());
		assertEquals(erin, list.at(4).get());

		Person franc = new Person(new PersonName("Franc"),new Priority(35));
		// overwrite alice
		list.put(new PersonId(1),franc,franc.priority.value);
		
		System.out.println(list.deepToString());

		
		assertEquals(bob, list.at(0).get());
		assertEquals(chuck, list.at(1).get());
		assertEquals(franc, list.at(2).get());
		assertEquals(dan, list.at(3).get());
		assertEquals(erin, list.at(4).get());

		System.out.println(list.deepToString());

	
	}

}
