Smallest example of the problem described by https://github.com/spring-projects/spring-session/issues/1424#issuecomment-769049024

To reproduce the bug:

- first visit localhost:8080 and check that only a single call to redis is made ("fetching session in cache (redis, etc.)" appears in logs)
- then go to localhost:8080/create-new
- visit localhost:8080 again and you can see that redis is called multiple times
