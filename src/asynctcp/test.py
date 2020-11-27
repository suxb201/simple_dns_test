def fun2(**kargs):
    print(kargs)
    fun1(**kargs)

def fun1(a,b=443,c=1):
    print((a,b,c))

fun2(1,a=2,b=3)