# 462-project
final project for cs462

currently supported client commands:
```
msg [username/groupname for receiver] [message]
creategroup [groupname] [members of the group delimited by 1 space]
disconnect
```

if a user is mentioned in creategroup or mesg and they aren't connected, they will be added as a user. When they connect the messages intended for them will be sent. There is a default group named 'all' that has all connected users.

Example:
 ```msg all hello world```
 will print
  ``` > [all] mitchell: hello world```
  for all connected users
  


also, spacing messed up when copying the files.

# There is a TODO list and list of ideas on the ConnectionHandler.java file.
