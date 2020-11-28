from selenium import webdriver
from selenium.common.exceptions import TimeoutException


def test(url):
    driver = webdriver.Firefox()
    driver.set_page_load_timeout(10)
    # driver.set_script_timeout(5)#这两种设置都进行才有效
    try:
        driver.get(url)
    except TimeoutException:
        driver.quit()
        return 'inf', 'inf'
    navigation_start = driver.execute_script("return window.performance.timing.navigationStart")
    response_start = driver.execute_script("return window.performance.timing.responseStart")
    dom_complete = driver.execute_script("return window.performance.timing.domComplete")
    print(dom_complete)
    ''' Calculate the performance'''
    backend_performance_calc = response_start - navigation_start
    frontend_performance_calc = dom_complete - response_start
    driver.quit()
    return backend_performance_calc, frontend_performance_calc


if __name__ == '__main__':

    for i in range(10):
        num1, num2 = test("https://bw.bilibili.com/2020/index.html")
        print(f"{num1:>5} {num2:>5}")

# https://www.aliyun.com/product/ecs
#
