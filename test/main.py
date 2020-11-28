from selenium import webdriver


def test(url):
    driver = webdriver.Chrome()
    driver.get(url)

    navigation_start = driver.execute_script("return window.performance.timing.navigationStart")
    response_start = driver.execute_script("return window.performance.timing.responseStart")
    dom_complete = driver.execute_script("return window.performance.timing.domComplete")

    ''' Calculate the performance'''
    backend_performance_calc = response_start - navigation_start
    frontend_performance_calc = dom_complete - response_start
    driver.quit()
    return backend_performance_calc, frontend_performance_calc


if __name__ == '__main__':
    url_list=[
        'https://tieba.baidu.com/f?kw=%E6%8A%97%E5%8E%8B%E8%83%8C%E9%94%85',
    ]
    for i in range(10):
        num1, num2 = test("https://tieba.baidu.com/f?kw=%E6%8A%97%E5%8E%8B%E8%83%8C%E9%94%85")
        print(f"{num1:>5} {num2:>5}")

# https://www.aliyun.com/product/ecs
#

