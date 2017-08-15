# Syntax
The ```caustic-runtime``` enables transactional accesses and modifications to any key-value store. However, key-value stores provide an extremely unintuitive interface for working with complex object models. The purpose of ```caustic-syntax``` is to define a more convenient language for expressing transactions on complex object models.

## Tries
The key idea behind the ```caustic-syntax``` library is that *all tries can be represented by a key-value store*. Therefore, we can perform familiar object interactions tries that map down to operations on a key-value store.

# Specification
## Objects
Each object is assigned a unique identifier and is composed of uniquely named fields and records. Identifiers, field names, and record names may not contain the following reserved characters:
- ```@@```: Field delimiter.
- ```$$```: Array delimiter.

Objects keep track of their various fields and records using the following *meta-fields*.
- ```__fields__```: ```$$``` delimited list of field names.
- ```__records__```: ```$$``` delimited list of record names.

### Fields
A field contains a value that is one of the following types:
- ```Flag```: Boolean
- ```Real```: Number
- ```Text```: String

Fields may be accessed and modified in the following manner:
- ```x.foo```: 
- ```x.foo = "bar"```
- ```x.foo += 5

#### References
A reference is a special kind of a field that contains an object identifier, which may be dereferenced.
- ```x.foo->bar```: ```bar``` field of the object referenced by ```x.foo```.
- ```x.foo = y```: Sets the ```foo``` field to point to the object ```y```.

### Records
A record is a nested object.
- ```x.foo.bar```: Returns the ```bar``` field of the record ```x.foo```.
- ```x.foo("bar")```: Returns the ```bar``` field of the record ```x.foo```.
- ```x.foo.bar += 3```: Updates the ```bar``` field of the record ```x.foo```.

## Variables
Internal variables are prefixed and suffixed by ```__```, so user variables should refrain from using them.
