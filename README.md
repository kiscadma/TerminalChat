# 462-project
final project for cs462

currently supported client commands:
```
msg [message receiver's name] [message]
creategroup [groupname] [members of the group delimited by 1 space]
disconnect
```

if a user is mentioned in creategroup or mesg and they aren't connected, they will be added as a user. When they connect the messages intended for them will be sent. There is a default group named 'all' that has all connected users.
